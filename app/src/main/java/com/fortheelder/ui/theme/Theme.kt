package com.fortheelder.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.unit.dp
import com.fortheelder.data.prefs.FontSize

// 两个 scheme 都是 private：外面只该通过 MaterialTheme.colorScheme 取色，
// 不该有人绕过主题直接拿某一套写死。可测性不靠放开可见性——对比度守卫断言的是
// Color.kt 里那些具名色值，另有一条源码级断言盯着这里的 wiring 有没有被改掉。
private val LightColors = lightColorScheme(
    primary = ElderGreen,
    onPrimary = WarmSurface,
    primaryContainer = ElderGreenContainer,
    onPrimaryContainer = OnElderGreenContainer,
    secondary = WarmBrown,
    onSecondary = WarmSurface,
    secondaryContainer = WarmBrownContainer,
    onSecondaryContainer = OnWarmBrownContainer,
    background = WarmBackground,
    onBackground = InkPrimary,
    surface = WarmSurface,
    onSurface = InkPrimary,
    surfaceVariant = WarmSurfaceVariant,
    onSurfaceVariant = InkSecondary,
    // 描边必须**不透明**且过 3:1。半透明描边在浅底色上合成出来的颜色只有 1.x:1，
    // 等于没有边界，而屏幕上看不出毛病。见 Color.kt 的 OutlineStrong。
    outline = OutlineStrong,
)

private val DarkColors = darkColorScheme(
    primary = ElderGreenLight,
    onPrimary = OnElderGreenLight,
    primaryContainer = ElderGreenContainerDark,
    onPrimaryContainer = ElderGreenContainer,
    secondary = WarmBrown,
    // 暗色下这一格不能再借 surfaceVariant：借了之后两张入口卡只剩文字能区分。
    secondaryContainer = WarmContainerDark,
    onSecondaryContainer = WarmBrownContainer,
    background = BackgroundDark,
    onBackground = InkOnDark,
    surface = SurfaceDark,
    onSurface = InkOnDark,
    surfaceVariant = SurfaceVariantDark,
    onSurfaceVariant = InkOnDarkSecondary,
    // 同一份边界语义，底色翻过来了，描边就要跟着翻，所以暗色另用一条更亮的。
    outline = OutlineStrongDark,
)

/** 圆角比 Material 默认更小：大圆角在大字号下容易和文字挤在一起。 */
private val ElderShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(18.dp),
    extraLarge = RoundedCornerShape(24.dp),
)

/**
 * 应用主题。
 *
 * 关键作用是**把应用内字号偏好乘进排版表**：主题按当前档位生成一套 `Typography`，
 * 于是偏好一改，全局文字一起变，不需要逐个界面传参，也不会漏掉任何一处。
 *
 * 为什么不用「替换 `LocalDensity.fontScale`」这种更省事的做法：实测不成立，见 [elderTypography] 的说明。
 * 用「换一套排版表」还有个附带好处——主题嵌套时不会累乘：内层永远只乘自己的档位，
 * 引导页因此可以放心地在最外层主题里再套一层预览主题。
 */
@Composable
fun ForTheElderTheme(
    fontSize: FontSize = FontSize.STANDARD,
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val typography = remember(fontSize) { elderTypography(fontSize.scale) }
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = typography,
        shapes = ElderShapes,
        content = content,
    )
}
