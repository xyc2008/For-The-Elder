package com.fortheelder.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * 配色。
 *
 * 选色依据是适老阅读的硬指标，而不是「好看」：
 * 1. 正文对比度 ≥ 7:1（WCAG AAA）。老年人晶状体透光率下降、对比敏感度变低，
 *    AA 的 4.5:1 在实际使用中仍然吃力。
 * 2. 非文本的界面边界 ≥ 3:1（WCAG 2.1 的 1.4.11 非文本对比度）。这条不是装饰指标：
 *    卡片、按钮、游戏格子全靠描边界定范围，边界看不见，老人家就分不清哪一块能点。
 *    目标是「与**相邻**颜色都要过」，所以同一条描边要对它挨着的每一层底色分别达标。
 * 3. 不用红色系做强调色。红色在健康类 App 里天然带「警报」意味，
 *    与「不制造焦虑」的定位冲突。
 *
 * 主色是偏冷的松绿，背景是暖白。冷绿给人「安静」，暖白降低屏幕刺眼程度。
 *
 * 这里的每一个数值都不是凭手感挑的：每一对「前景/背景」都由
 * `app/src/test/java/com/fortheelder/ui/theme/PaletteContrastTest.kt` 按 WCAG 公式逐个断言，
 * 括号里给出的比值就是那条用例算出来的数。**改了颜色而没改断言，构建会直接失败**，
 * 这是有意的——颜色是这一层唯一会「悄悄变得不好用」的东西，肉眼看不出来。
 *
 * 亮暗两套都要单独达标：系统切到深色模式时，同一对语义词换了值，对比度必须重新算一遍，
 * 不能靠「亮色过了暗色差不多」这种推断。
 */

// 亮色主题

/**
 * 主色。它同时担三种角色：按钮底色（配 [WarmSurface] 当标签色）、区块小标题的文字色
 * （落在 [WarmBackground] 上）、入口卡的文字色。三种用途里最紧的那一条要求 ≥ 7:1，
 * 反推出主色的相对亮度上限约 0.0916。
 *
 * `#175C46` 的亮度约 0.0828，是留了余量的取值：作按钮标签 7.91:1、作正文落在 [WarmBackground] 上 7.46:1。
 * 上一版的 `#1F6E5C` 只有 6.10:1 与 5.76:1，**两条都不够**，这一版把它调深。
 */
val ElderGreen = Color(0xFF175C46)

val ElderGreenContainer = Color(0xFFD3E9E1)
val OnElderGreenContainer = Color(0xFF0B2E25)

val WarmBrown = Color(0xFF6B4E2E)
val WarmBrownContainer = Color(0xFFF3E5D3)
val OnWarmBrownContainer = Color(0xFF2B1F10)

val WarmBackground = Color(0xFFFBF8F3)
val WarmSurface = Color(0xFFFFFFFF)
val WarmSurfaceVariant = Color(0xFFF1ECE3)

/** 正文色。对 [WarmBackground] 的对比度约 16.15:1。 */
val InkPrimary = Color(0xFF1B1C1A)

/** 次要文字。对 [WarmBackground] 的对比度约 7.80:1，仍然满足 AAA。 */
val InkSecondary = Color(0xFF4A504C)

/**
 * 亮色主题的描边色，供 `colorScheme.outline` 与所有卡片、格子、行的边框使用。
 *
 * 它是**不透明**的：上一版把 `#8C938E` 再乘 0.35 的透明度，合成出来的实际颜色约 `#D7D9D8`，
 * 对白底只有 1.42:1、对暖白底 1.39:1，离 1.4.11 要的 3:1 差得很远。
 * 半透明描边在浅底色上等于「看不见」，而它在屏幕上完全看不出毛病。
 *
 * `#7E857F` 的亮度约 0.2274，对三种亮色底都过了 3:1：
 * [WarmSurface] 3.78:1、[WarmBackground] 3.57:1、[WarmSurfaceVariant] 3.22:1。
 */
val OutlineStrong = Color(0xFF7E857F)

// 暗色主题

val ElderGreenLight = Color(0xFF8FD5C2)
val OnElderGreenLight = Color(0xFF00382C)
val ElderGreenContainerDark = Color(0xFF0C5343)

/**
 * 暗色主题里与绿色容器成对出现的暖色容器。
 *
 * 这一格原本由 [SurfaceVariantDark] 兼任，后果是两张入口卡在暗色下**长得一模一样**——
 * 亮色里靠绿/暖两色区分「想说话」与「手上想有点事」，切到深色就只剩文字能区分了。
 * 补一个暖色暗容器把这个区分找回来；[WarmBrownContainer] 落在它上面是 10.07:1。
 */
val WarmContainerDark = Color(0xFF3E3222)

val SurfaceDark = Color(0xFF121413)
val SurfaceVariantDark = Color(0xFF2A2E2C)
val BackgroundDark = Color(0xFF0E100F)
val InkOnDark = Color(0xFFE8EAE7)
val InkOnDarkSecondary = Color(0xFFC3C8C4)

/**
 * 暗色主题的描边色。它比亮色那条**更亮**：同样的边界语义，底色翻过来了，
 * 前景色就得跟着翻。`#8C938E` 对 [SurfaceDark] 5.88:1、对 [BackgroundDark] 6.07:1，
 * 对 [SurfaceVariantDark] 也有约 4.4:1。
 */
val OutlineStrongDark = Color(0xFF8C938E)
