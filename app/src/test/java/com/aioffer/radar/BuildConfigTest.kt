package com.aioffer.radar

import org.junit.Assert.assertTrue
import org.junit.Test

class BuildConfigTest {

    @Test
    fun debugBuild_hasCollectorEndpointConfigured() {
        assertTrue(BuildConfig.DEBUG)
        assertTrue(BuildConfig.GLM_COLLECTOR_URL.isNotBlank())
        assertTrue(
            "Collector endpoint should be an HTTP(S) URL",
            BuildConfig.GLM_COLLECTOR_URL.startsWith("http://") ||
                BuildConfig.GLM_COLLECTOR_URL.startsWith("https://")
        )
    }
}
