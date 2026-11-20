package com.fortheelder.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * 配色。
 *
 * 选色依据是适老阅读的两条硬指标，而不是「好看」：
 * 1. 正文对比度 ≥ 7:1（WCAG AAA）。老年人晶状体透光率下降、对比敏感度变低，
 *    AA 的 4.5:1 在实际使用中仍然吃力。
 * 2. 不用红色系做强调色。红色在健康类 App 里天然带「警报」意味，
 *    与「不制造焦虑」的定位冲突。
 *
 * 主色是偏冷的松绿，背景是暖白。冷绿给人「安静」，暖白降低屏幕刺眼程度。
 */

// 亮色主题
val ElderGreen = Color(0xFF1F6E5C)
val ElderGreenDark = Color(0xFF14503F)
val ElderGreenContainer = Color(0xFFD3E9E1)
val OnElderGreenContainer = Color(0xFF0B2E25)

val WarmBrown = Color(0xFF6B4E2E)
val WarmBrownContainer = Color(0xFFF3E5D3)
val OnWarmBrownContainer = Color(0xFF2B1F10)

val WarmBackground = Color(0xFFFBF8F3)
val WarmSurface = Color(0xFFFFFFFF)
val WarmSurfaceVariant = Color(0xFFF1ECE3)

/** 正文色。对 [WarmBackground] 的对比度约 15:1。 */
val InkPrimary = Color(0xFF1B1C1A)

/** 次要文字。对 [WarmBackground] 的对比度约 7.4:1，仍然满足 AAA。 */
val InkSecondary = Color(0xFF4A504C)

/** 分隔线与描边，只用于装饰，其上不放文字。 */
val OutlineSoft = Color(0xFF8C938E)

// 暗色主题
val ElderGreenLight = Color(0xFF8FD5C2)
val OnElderGreenLight = Color(0xFF00382C)
val ElderGreenContainerDark = Color(0xFF0C5343)
val SurfaceDark = Color(0xFF121413)
val SurfaceVariantDark = Color(0xFF2A2E2C)
val BackgroundDark = Color(0xFF0E100F)
val InkOnDark = Color(0xFFE8EAE7)
val InkOnDarkSecondary = Color(0xFFC3C8C4)
