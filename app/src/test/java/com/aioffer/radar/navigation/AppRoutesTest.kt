package com.aioffer.radar.navigation

import org.junit.Assert.assertEquals
import org.junit.Test

class AppRoutesTest {
    @Test
    fun homeRoute_isStable() {
        assertEquals("home", AppRoutes.HOME)
    }

    @Test
    fun statisticsRoute_isStable() {
        assertEquals("statistics", AppRoutes.STATISTICS)
    }

    @Test
    fun settingsRoute_isStable() {
        assertEquals("settings", AppRoutes.SETTINGS)
    }

    @Test
    fun detailRoute_encodesProductId() {
        assertEquals("detail/{productId}", AppRoutes.DETAIL)
        assertEquals("detail/demo%2Fkimi%20pro", AppRoutes.productDetail("demo/kimi pro"))
    }
}
