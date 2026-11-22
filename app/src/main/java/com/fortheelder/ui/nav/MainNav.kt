package com.fortheelder.ui.nav

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.fortheelder.core.AppContainer
import com.fortheelder.ui.home.HomeScreen
import com.fortheelder.ui.onboard.OnboardingScreen
import com.fortheelder.ui.play.PlayScreen
import com.fortheelder.ui.settings.SettingsScreen
import com.fortheelder.ui.talk.TalkScreen

/**
 * 目的地名。
 *
 * 用常量而不是字符串字面量：写错 route 名在运行期才崩，而且崩出来是
 * 「navigation destination not found」这种不好定位的错。
 *
 * [ONBOARDING_REVIEW] 是**引导的只读重看**（第 47 轮），入口只有设置页那一条。它不是把
 * 引导那道闸门搬进了导航图：闸门的判定仍然在 [com.fortheelder.ui.AppRoot] 里由
 * `onboardingDone` 说了算（ADR-002），这一条只是 `NavHost` 里的普通目的地——进得来、
 * 退得出去，系统返回键天然可用，也永远不会写 `onboardingDone`。
 */
object Route {
    const val HOME = "home"
    const val SETTINGS = "settings"
    const val TALK = "talk"
    const val PLAY = "play"

    /** 只读重看引导三页。为什么它当普通目的地就够，见本对象的注释。 */
    const val ONBOARDING_REVIEW = "onboarding-review"
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
                onOpenTalk = { navController.navigate(Route.TALK) },
                onOpenPlay = { navController.navigate(Route.PLAY) },
            )
        }
        composable(Route.SETTINGS) {
            SettingsScreen(
                container = container,
                onBack = { navController.popBackStack() },
                onReviewOnboarding = { navController.navigate(Route.ONBOARDING_REVIEW) },
            )
        }
        // 只读重看引导三页。设置还没读出来就先不画（照 AppRoot 的先例），读完只画一帧旧值也不会错。
        composable(Route.ONBOARDING_REVIEW) {
            val resolved = container.settings.settings.collectAsState(initial = null).value
            if (resolved != null) {
                OnboardingScreen(
                    container = container,
                    initialAddress = resolved.address,
                    initialFontSize = resolved.fontSize,
                    onReviewExit = { navController.popBackStack() },
                )
            }
        }
        composable(Route.TALK) {
            TalkScreen(
                container = container,
                onBack = { navController.popBackStack() },
            )
        }
        composable(Route.PLAY) {
            PlayScreen(
                container = container,
                onBack = { navController.popBackStack() },
            )
        }
    }
}
