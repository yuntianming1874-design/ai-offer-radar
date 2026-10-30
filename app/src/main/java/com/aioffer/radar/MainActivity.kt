package com.aioffer.radar

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.mutableStateOf
import com.aioffer.radar.navigation.AiOfferRadarApp
import com.aioffer.radar.navigation.AppRoutes
import com.aioffer.radar.navigation.ProductDeepLinkRequest
import com.aioffer.radar.ui.theme.AiOfferRadarTheme

class MainActivity : ComponentActivity() {
    private val deepLinkRequest = mutableStateOf<ProductDeepLinkRequest?>(null)
    private var nextRequestId = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // 配置变更时 NavController 会从 saved state 恢复页面，不能再消费
        // Activity.intent 里的旧深链，否则会把已经返回首页的用户拉回详情。
        // 注意：不调用 setIntent；ActivityScenario 等测试工具会用原始
        // launch intent 匹配 Activity，改写 intent 会让生命周期事件被忽略。
        if (savedInstanceState == null) {
            parseDeepLink(intent)?.let(::acceptDeepLink)
        }
        setContent {
            AiOfferRadarTheme {
                AiOfferRadarApp(deepLinkRequest.value)
            }
        }
    }

    public override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        parseDeepLink(intent)?.let(::acceptDeepLink)
    }

    private fun acceptDeepLink(productId: String) {
        deepLinkRequest.value = ProductDeepLinkRequest(productId, nextRequestId++)
    }

    private fun parseDeepLink(intent: Intent): String? {
        val productId = intent.getStringExtra(AppRoutes.PRODUCT_ID)
            ?: intent.data?.takeIf { it.scheme == "aioffer" && it.host == "product" }?.lastPathSegment
        return productId?.takeIf { it.isNotBlank() }
    }
}
