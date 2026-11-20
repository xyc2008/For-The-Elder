package com.fortheelder.ui.nav

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.fortheelder.core.AppContainer
import com.fortheelder.ui.home.HomeScreen
import com.fortheelder.ui.settings.SettingsScreen

/**
 * 目的地名。
 *
 * 用常量而不是字符串字面量：写错 route 名在运行期才崩，而且崩出来是
 * 「navigation destination not found」这种不好定位的错。
 */
object Route {
    const val HOME = "home"
    const val SETTINGS = "settings"
}

/**
 * 主导航。
 *
 * 只登记**已经做完并且能跑通**的页面。不预埋空壳页面来占位——导航栏里出现一个点进去
 * 什么都没有的入口，比少一个入口更糟。
 */
@Composable
fun MainNav(container: AppContainer) {
    val navController = rememberNavController()

    NavHost(navController = navController, startDestination = Route.HOME) {
        composable(Route.HOME) {
            HomeScreen(
                container = container,
                onOpenSettings = { navController.navigate(Route.SETTINGS) },
            )
        }
        composable(Route.SETTINGS) {
            SettingsScreen(
                container = container,
                onBack = { navController.popBackStack() },
            )
        }
    }
}
