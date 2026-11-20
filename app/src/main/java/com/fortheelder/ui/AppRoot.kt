package com.fortheelder.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.runtime.collectAsState
import com.fortheelder.core.AppContainer
import com.fortheelder.data.prefs.FontSize
import com.fortheelder.ui.nav.MainNav
import com.fortheelder.ui.onboard.OnboardingScreen
import com.fortheelder.ui.theme.ForTheElderTheme

/**
 * 应用的根组合。
 *
 * 两件事值得说明：
 *
 * 1. **引导页在导航图之外，是一道闸门**。它没有 route、不能被 deep link 打开，
 *    也没有「跳过」以外的入口。放在 NavHost 里的常见问题是引导完成后需要
 *    `popUpTo` 把自身弹掉，写错就会退化成「返回键又能回到引导页」。
 *    这里 `onboardingDone` 一翻真，整个分支直接换成 [MainNav]，不存在回退路径。
 *
 * 2. **首帧不画内容**。`settings` 是异步读盘，null 表示还没读到。这时如果按默认值渲染，
 *    会先闪一下「标准字号」再跳成用户选的字号。宁可多留一帧背景色。
 *
 * `safeDrawingPadding` 处理状态栏/导航栏/刘海/键盘四类遮挡：targetSdk 35 起系统强制
 * 边到边渲染，不处理的话标题会被状态栏压住。`safeDrawing` 已经包含 IME，所以键盘弹出时
 * 内容会自动上移，不需要再单独加 `imePadding`。
 */
@Composable
fun AppRoot(container: AppContainer) {
    LaunchedEffect(Unit) {
        container.settings.ensureFirstLaunchRecorded()
    }

    val settings by container.settings.settings.collectAsState(initial = null)

    ForTheElderTheme(fontSize = settings?.fontSize ?: FontSize.STANDARD) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding(),
            color = MaterialTheme.colorScheme.background,
        ) {
            val resolved = settings
            when {
                resolved == null -> Unit
                !resolved.onboardingDone -> OnboardingScreen(
                    container = container,
                    initialAddress = resolved.address,
                    initialFontSize = resolved.fontSize,
                )
                else -> MainNav(container = container)
            }
        }
    }
}
