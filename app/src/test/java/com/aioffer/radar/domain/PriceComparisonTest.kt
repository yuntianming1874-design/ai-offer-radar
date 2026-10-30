package com.aioffer.radar.domain

import com.aioffer.radar.core.model.BillingPeriod
import com.aioffer.radar.core.model.Currency
import com.aioffer.radar.core.model.Region
import com.aioffer.radar.core.testing.BASE_COMPARISON_KEY
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PriceComparisonTest {
    @Test
    fun identicalKeys_canBeCompared() {
        assertTrue(BASE_COMPARISON_KEY.canCompareWith(BASE_COMPARISON_KEY.copy()))
    }

    @Test
    fun differentProduct_cannotBeCompared() {
        assertFalse(BASE_COMPARISON_KEY.canCompareWith(BASE_COMPARISON_KEY.copy(productId = "glm")))
    }

    @Test
    fun differentPlan_cannotBeCompared() {
        assertFalse(
            BASE_COMPARISON_KEY.canCompareWith(BASE_COMPARISON_KEY.copy(planId = "kimi-student"))
        )
    }

    @Test
    fun differentRegion_cannotBeCompared() {
        assertFalse(BASE_COMPARISON_KEY.canCompareWith(BASE_COMPARISON_KEY.copy(region = Region("HK"))))
    }

    @Test
    fun differentCurrency_cannotBeCompared() {
        assertFalse(
            BASE_COMPARISON_KEY.canCompareWith(BASE_COMPARISON_KEY.copy(currency = Currency.USD))
        )
    }

    @Test
    fun differentBillingPeriod_cannotBeCompared() {
        assertFalse(
            BASE_COMPARISON_KEY.canCompareWith(
                BASE_COMPARISON_KEY.copy(billingPeriod = BillingPeriod.YEARLY)
            )
        )
    }

    @Test
    fun differentTaxBasis_cannotBeCompared() {
        assertFalse(BASE_COMPARISON_KEY.canCompareWith(BASE_COMPARISON_KEY.copy(taxIncluded = false)))
    }
}
