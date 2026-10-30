package com.aioffer.radar.data

import com.aioffer.radar.core.model.BillingPeriod
import com.aioffer.radar.core.model.ComparisonKey
import com.aioffer.radar.core.model.Currency
import com.aioffer.radar.core.model.DataSourceType
import com.aioffer.radar.core.model.Money
import com.aioffer.radar.core.model.Offer
import com.aioffer.radar.core.model.OfferType
import com.aioffer.radar.core.model.Plan
import com.aioffer.radar.core.model.PlanType
import com.aioffer.radar.core.model.Price
import com.aioffer.radar.core.model.Product
import com.aioffer.radar.core.model.Region
import com.aioffer.radar.core.model.SourceInfo
import com.aioffer.radar.core.network.RemoteOfferDto
import com.aioffer.radar.core.network.RemotePlanDto
import com.aioffer.radar.core.network.RemoteProductDto
import com.aioffer.radar.core.network.RemoteSourceDto
import com.aioffer.radar.core.network.RemoteSyncPayload
import java.time.Instant

class RemotePayloadNormalizationException(message: String, cause: Throwable? = null) :
    Exception(message, cause)

data class NormalizedPayload(
    val source: SourceInfo,
    val products: List<Product>,
    val plans: List<Plan>,
    val offers: List<Offer>
)

object RemotePayloadNormalizer {
    fun normalize(payload: RemoteSyncPayload): NormalizedPayload {
        val source = normalizeSource(payload.source)
        val isDemo = source.type == DataSourceType.DEMO
        validate(isDemo || !source.sourceUrl.isNullOrBlank()) {
            "Official sources must provide a source URL"
        }
        validate(source.fetchedAt > Instant.EPOCH) {
            "Source fetch time is required"
        }
        // Demo and official business IDs must be disjoint. The source ID is the
        // key that all stored offers and snapshots hang from, so it must never
        // be reused across the demo/official boundary either.
        validate(isDemo == source.id.startsWith("demo-")) {
            "Source IDs must use the demo- prefix for demo data and no prefix for official data"
        }
        validate(payload.products.all { it.isDemo == isDemo }) {
            "Demo and official product data must remain separated"
        }
        validateUniqueIds(payload.products.map { it.id }, "product")
        validateUniqueIds(payload.plans.map { it.id }, "plan")
        validateUniqueIds(payload.offers.map { it.id }, "offer")

        val products = payload.products.map { normalizeProduct(it, isDemo) }
        val plans = payload.plans.map { normalizePlan(it, products, isDemo) }
        val plansById = plans.associateBy { it.id }
        val offers = payload.offers.map { offer ->
            normalizeOffer(offer, source, plansById, isDemo)
        }
        return NormalizedPayload(source, products, plans, offers)
    }

    private fun normalizeSource(source: RemoteSourceDto): SourceInfo {
        validate(source.id.isNotBlank()) { "Source ID must not be blank" }
        validate(source.titleOrSummary.isNotBlank()) { "Source title or summary must not be blank" }
        return SourceInfo(
            id = source.id,
            type = parseEnum<DataSourceType>(source.sourceType, "source type"),
            sourceUrl = source.sourceUrl,
            titleOrSummary = source.titleOrSummary,
            fetchedAt = Instant.ofEpochMilli(source.fetchedAtEpochMillis)
        )
    }

    private fun normalizeProduct(product: RemoteProductDto, isDemo: Boolean): Product {
        validate(product.id.isNotBlank()) { "Product ID must not be blank" }
        validate(product.name.isNotBlank()) { "Product name must not be blank" }
        validate(isDemo == product.id.startsWith("demo-")) {
            "Product IDs must use the demo- prefix for demo data and no prefix for official data"
        }
        return Product(product.id, product.name, product.logoUrl, product.isDemo)
    }

    private fun normalizePlan(
        plan: RemotePlanDto,
        products: List<Product>,
        isDemo: Boolean
    ): Plan {
        validate(plan.id.isNotBlank()) { "Plan ID must not be blank" }
        validate(isDemo == plan.id.startsWith("demo-")) {
            "Plan IDs must use the demo- prefix for demo data and no prefix for official data"
        }
        validate(products.any { it.id == plan.productId }) {
            "Plan ${plan.id} references an unknown product"
        }
        return Plan(
            id = plan.id,
            productId = plan.productId,
            name = plan.name,
            type = parseEnum<PlanType>(plan.planType, "plan type")
        )
    }

    private fun normalizeOffer(
        offer: RemoteOfferDto,
        source: SourceInfo,
        plans: Map<String, Plan>,
        isDemo: Boolean
    ): Offer {
        validate(offer.source.id == source.id) { "Offer and payload source IDs must match" }
        validate(isDemo == offer.id.startsWith("demo-")) {
            "Offer IDs must use the demo- prefix for demo data and no prefix for official data"
        }
        validate(offer.title.isNotBlank()) { "Offer title must not be blank" }
        val plan = plans[offer.planId]
        validate(plan != null && plan.productId == offer.productId) {
            "Offer ${offer.id} references an unknown or mismatched plan"
        }
        val planType = parseEnum<PlanType>(offer.planType, "offer plan type")
        validate(plan?.type == planType) { "Offer and plan types must match" }
        validate(offer.regionCode.isNotBlank()) { "Region must not be blank" }
        validate(offer.originalAmountMinor >= 0 && offer.actualAmountMinor >= 0) {
            "Offer amounts must not be negative"
        }
        validate(offer.monthlyEquivalentMinor == null || offer.monthlyEquivalentMinor >= 0) {
            "Monthly equivalent must not be negative"
        }
        val startsAt = Instant.ofEpochMilli(offer.startsAtEpochMillis)
        val endsAt = offer.endsAtEpochMillis?.let(Instant::ofEpochMilli)
        validate(endsAt == null || endsAt.isAfter(startsAt)) {
            "Offer end time must be after its start time"
        }
        val currency = parseEnum<Currency>(offer.currencyCode, "currency")
        return Offer(
            id = offer.id,
            comparisonKey = ComparisonKey(
                productId = offer.productId,
                planId = offer.planId,
                region = Region(offer.regionCode),
                currency = currency,
                billingPeriod = parseEnum<BillingPeriod>(offer.billingPeriod, "billing period"),
                taxIncluded = offer.taxIncluded
            ),
            planType = planType,
            offerType = parseEnum<OfferType>(offer.offerType, "offer type"),
            title = offer.title,
            discountDescription = offer.discountDescription,
            price = Price(
                originalAmount = Money(offer.originalAmountMinor, currency),
                actualAmount = Money(offer.actualAmountMinor, currency),
                monthlyEquivalent = offer.monthlyEquivalentMinor?.let { Money(it, currency) }
            ),
            startsAt = startsAt,
            endsAt = endsAt,
            source = source,
            updatedAt = Instant.ofEpochMilli(offer.updatedAtEpochMillis),
            officialUrl = offer.officialUrl
        )
    }

    private fun validateUniqueIds(ids: List<String>, type: String) {
        validate(ids.size == ids.toSet().size) { "Duplicate remote $type IDs are not allowed" }
    }

    private inline fun <reified T : Enum<T>> parseEnum(value: String, field: String): T =
        runCatching { enumValueOf<T>(value) }.getOrElse {
            throw RemotePayloadNormalizationException("Unknown $field: $value", it)
        }

    private fun validate(condition: Boolean, message: () -> String) {
        if (!condition) throw RemotePayloadNormalizationException(message())
    }
}
