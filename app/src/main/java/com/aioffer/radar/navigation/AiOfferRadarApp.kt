package com.aioffer.radar.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.aioffer.radar.AiOfferRadarApplication
import com.aioffer.radar.feature.detail.DetailScreen
import com.aioffer.radar.feature.detail.DetailViewModel
import com.aioffer.radar.feature.home.HomeScreen
import com.aioffer.radar.feature.home.HomeViewModel
import com.aioffer.radar.feature.home.rememberNotificationPermissionState
import com.aioffer.radar.feature.settings.SettingsScreen
import com.aioffer.radar.feature.settings.SettingsViewModel
import com.aioffer.radar.feature.statistics.StatisticsScreen
import com.aioffer.radar.feature.statistics.StatisticsViewModel
import java.net.URLEncoder

object AppRoutes {
    const val HOME = "home"
    const val STATISTICS = "statistics"
    const val SETTINGS = "settings"
    const val PRODUCT_ID = "productId"
    const val DETAIL = "detail/{$PRODUCT_ID}"

    fun productDetail(productId: String): String =
        "detail/${URLEncoder.encode(productId, "UTF-8").replace("+", "%20")}"
}

data class ProductDeepLinkRequest(val productId: String, val requestId: Long)

@Composable
fun AiOfferRadarApp(deepLinkRequest: ProductDeepLinkRequest? = null) {
    val navController = rememberNavController()
    val application = LocalContext.current.applicationContext as AiOfferRadarApplication
    val notificationPermissionState = rememberNotificationPermissionState()
    val homeViewModel: HomeViewModel = viewModel(factory = application.container.homeViewModelFactory())

    LaunchedEffect(deepLinkRequest) {
        val request = deepLinkRequest ?: return@LaunchedEffect
        val currentEntry = navController.currentBackStackEntry
        val alreadyShowingRequestedProduct =
            currentEntry?.destination?.route == AppRoutes.DETAIL &&
                currentEntry.arguments?.getString(AppRoutes.PRODUCT_ID) == request.productId
        if (alreadyShowingRequestedProduct) {
            return@LaunchedEffect
        }

        // 清掉 home 之上的页面，再压入目标详情，保证一次返回即可到首页。
        // 不设置 saveState：被替换的详情页不应留下可恢复的旧状态；
        // 也不用 launchSingleTop，避免相同路由不同 productId 时复用旧
        // NavBackStackEntry，导致 DetailViewModel 仍绑定上一个产品。
        navController.navigate(AppRoutes.productDetail(request.productId)) {
            popUpTo(AppRoutes.HOME) {
                inclusive = false
            }
        }
    }

    NavHost(
        navController = navController,
        startDestination = AppRoutes.HOME
    ) {
        composable(AppRoutes.HOME) {
            HomeScreen(
                viewModel = homeViewModel,
                notificationsEnabled = notificationPermissionState.isGranted,
                notificationPermissionDenied = notificationPermissionState.wasDenied,
                onNotificationPermissionRequest = notificationPermissionState::request,
                onProductClick = { productId ->
                    navController.navigate(AppRoutes.productDetail(productId))
                },
                onStatisticsClick = { navController.navigate(AppRoutes.STATISTICS) },
                onSettingsClick = { navController.navigate(AppRoutes.SETTINGS) }
            )
        }
        composable(AppRoutes.SETTINGS) {
            val settingsViewModel: SettingsViewModel = viewModel(
                factory = application.container.settingsViewModelFactory()
            )
            SettingsScreen(
                viewModel = settingsViewModel,
                notificationsEnabled = notificationPermissionState.isGranted,
                notificationPermissionDenied = notificationPermissionState.wasDenied,
                onNotificationPermissionRequest = notificationPermissionState::request,
                onBack = navController::popBackStack
            )
        }
        composable(AppRoutes.STATISTICS) {
            val statisticsViewModel: StatisticsViewModel = viewModel(
                factory = application.container.statisticsViewModelFactory()
            )
            StatisticsScreen(
                viewModel = statisticsViewModel,
                onBack = navController::popBackStack
            )
        }
        composable(AppRoutes.DETAIL) { backStackEntry ->
            val productId = requireNotNull(backStackEntry.arguments?.getString(AppRoutes.PRODUCT_ID))
            val detailViewModel: DetailViewModel = viewModel(
                factory = application.container.detailViewModelFactory(productId)
            )
            DetailScreen(
                viewModel = detailViewModel,
                onBack = navController::popBackStack
            )
        }
    }
}
