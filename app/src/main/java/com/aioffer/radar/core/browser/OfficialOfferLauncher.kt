package com.aioffer.radar.core.browser

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.browser.customtabs.CustomTabsIntent

enum class OfficialLaunchResult {
    SUCCESS,
    NO_BROWSER,
    OPEN_FAILED
}

interface OfficialLinkLauncher {
    fun open(url: String): OfficialLaunchResult
}

class AndroidOfficialOfferLauncher(
    private val context: Context,
    private val customTabLaunch: (Context, Uri) -> Unit = { launchContext, uri ->
        val customTabsIntent = CustomTabsIntent.Builder().build().apply {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        customTabsIntent.launchUrl(launchContext, uri)
    },
    private val browserLaunch: (Context, Uri) -> Unit = { launchContext, uri ->
        val intent = Intent(Intent.ACTION_VIEW, uri).apply {
            addCategory(Intent.CATEGORY_BROWSABLE)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        launchContext.startActivity(intent)
    }
) : OfficialLinkLauncher {
    override fun open(url: String): OfficialLaunchResult {
        val uri = Uri.parse(url)
        try {
            customTabLaunch(context, uri)
            return OfficialLaunchResult.SUCCESS
        } catch (_: Exception) {
        }

        return try {
            browserLaunch(context, uri)
            OfficialLaunchResult.SUCCESS
        } catch (_: ActivityNotFoundException) {
            OfficialLaunchResult.NO_BROWSER
        } catch (_: SecurityException) {
            OfficialLaunchResult.NO_BROWSER
        } catch (_: Exception) {
            OfficialLaunchResult.OPEN_FAILED
        }
    }
}
