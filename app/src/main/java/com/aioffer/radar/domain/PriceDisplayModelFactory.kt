package com.aioffer.radar.domain

import com.aioffer.radar.core.model.BillingPeriod
import com.aioffer.radar.core.model.Money
import com.aioffer.radar.core.model.Offer
import com.aioffer.radar.core.model.Price
import java.math.BigDecimal
import java.math.RoundingMode

data class PriceDisplayModel(
    val totalAmount: Money,
    val billingPeriod: BillingPeriod,
    val monthlyEquivalent: Money?
)

object PriceDisplayModelFactory {
    fun create(offer: Offer): PriceDisplayModel {
        require(offer.price.actualAmount.currency == offer.comparisonKey.currency) {
            "Price currency must match the comparison key currency"
        }
        return create(offer.price, offer.comparisonKey.billingPeriod)
    }

    fun create(price: Price, billingPeriod: BillingPeriod): PriceDisplayModel {
        price.monthlyEquivalent?.let { monthly ->
            require(monthly.currency == price.actualAmount.currency) {
                "Monthly equivalent currency must match the total price currency"
            }
        }

        val months = billingPeriod.months
        val monthlyEquivalent = when {
            months == null -> null
            price.monthlyEquivalent != null -> price.monthlyEquivalent
            else -> Money(
                amountMinor = BigDecimal.valueOf(price.actualAmount.amountMinor)
                    .divide(BigDecimal.valueOf(months.toLong()), 0, RoundingMode.HALF_UP)
                    .longValueExact(),
                currency = price.actualAmount.currency
            )
        }

        return PriceDisplayModel(
            totalAmount = price.actualAmount,
            billingPeriod = billingPeriod,
            monthlyEquivalent = monthlyEquivalent
        )
    }
}
