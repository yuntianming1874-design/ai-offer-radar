package com.aioffer.radar.core.browser

import android.content.ActivityNotFoundException
import android.content.Context
import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class OfficialOfferLauncherTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val url = "https://example.com/subscribe"

    @Test
    fun availableCustomTab_isPreferredWithoutBrowserFallback() {
        var customTabUri: Uri? = null
        var browserOpened = false
        val launcher = AndroidOfficialOfferLauncher(
            context = context,
            customTabLaunch = { _, uri -> customTabUri = uri },
            browserLaunch = { _, _ -> browserOpened = true }
        )

        val result = launcher.open(url)

        assertEquals(OfficialLaunchResult.SUCCESS, result)
        assertEquals(url, customTabUri.toString())
        assertTrue(!browserOpened)
    }

    @Test
    fun unavailableCustomTab_fallsBackToBrowserOnce() {
        val browserUris = mutableListOf<Uri>()
        val launcher = AndroidOfficialOfferLauncher(
            context = context,
            customTabLaunch = { _, _ -> throw ActivityNotFoundException() },
            browserLaunch = { _, uri -> browserUris += uri }
        )

        val result = launcher.open(url)

        assertEquals(OfficialLaunchResult.SUCCESS, result)
        assertEquals(listOf(url), browserUris.map { it.toString() })
    }

    @Test
    fun noAvailableBrowser_returnsErrorWithoutThrowing() {
        val launcher = AndroidOfficialOfferLauncher(
            context = context,
            customTabLaunch = { _, _ -> throw ActivityNotFoundException() },
            browserLaunch = { _, _ -> throw ActivityNotFoundException() }
        )

        val result = launcher.open(url)

        assertEquals(OfficialLaunchResult.NO_BROWSER, result)
    }

    @Test
    fun unexpectedBrowserFailure_returnsOpenFailed() {
        val launcher = AndroidOfficialOfferLauncher(
            context = context,
            customTabLaunch = { _, _ -> throw ActivityNotFoundException() },
            browserLaunch = { _: Context, _: Uri -> error("Launch failed") }
        )

        assertEquals(OfficialLaunchResult.OPEN_FAILED, launcher.open(url))
    }
}
