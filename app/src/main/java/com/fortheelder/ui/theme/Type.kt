package com.fortheelder.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * 字体。
 *
 * 与 Material 默认排版相比整体放大：Material 的 `bodyLarge` 是 16sp，这里起点就是 20sp。
 * 理由很直接——目标用户里超过一半戴着老花镜，16sp 的中文正文在 6 英寸屏上需要费力辨认。
 *
 * 行高给得比 Material 默认更松（约 1.45 倍）：中文没有单词边界，行距太紧会串行。
 *
 * ## 应用内字号档位是怎么生效的
 *
 * **不是**替换 `LocalDensity.fontScale`，而是把档位倍率直接乘进每个 `sp`。
 * 这是实测之后改的做法，原始做法在模拟器上不成立（见 `records/04-质量与验证记录.md`）：
 * 替换密度之后，20sp 的正文只放大了 9.6%（标称 15%），30sp 以上的标题**完全不放大**。
 * 直接乘 sp 没有任何中间环节可以丢信息，20sp 变 26sp 就是 26sp。
 *
 * 系统字号仍然由平台密度决定，本档位**叠加在其之上**，而不是把它抹掉：
 * 用户把系统字号调大说明他确实看不清，应用内再选「更大」应当更大，而不是回到 1.0。
 *
 * 注意这里**不是**干净的乘法关系。实测（API 35，见 `records/04-质量与验证记录.md`）表明
 * 平台对 `fontScale` 做非线性字体换算，字号越大压制越狠：系统 1.5 只兑现 1.309×，
 * 在其之上再选应用内「更大」只额外兑现 1.100×（标称 1.30×）。
 * 也就是说系统字号被调大后，三档之间的区分度会下降——这是平台行为，本应用刻意不掰直
 * （掰直等于覆盖用户手机上的系统级设置，属可访问性反模式）。详见 ADR-004。
 *
 * 行高跟着字号一起乘，否则大字号下文字会被行高裁掉。
 * 字距不乘：Material 给正文的 0.5sp 字距是排版微调，跟着放大会让中文显得松散。
 */
private val materialBase: Typography = Typography()

private fun TextStyle.scaled(sizeSp: Float, lineHeightSp: Float, scale: Float, weight: FontWeight? = null): TextStyle =
    copy(
        fontSize = (sizeSp * scale).sp,
        lineHeight = (lineHeightSp * scale).sp,
        fontWeight = weight ?: fontWeight,
    )

/**
 * 按字号档位生成排版表。
 *
 * 每次调用都会新建 15 个 `TextStyle` 对象，所以调用方必须把它记住
 * （主题里用 `remember(fontSize)`，见 [ForTheElderTheme]），不要在重组路径上直接调用。
 */
fun elderTypography(scale: Float = 1f): Typography = materialBase.copy(
    displaySmall = materialBase.displaySmall.scaled(40f, 54f, scale, FontWeight.Medium),
    headlineLarge = materialBase.headlineLarge.scaled(34f, 46f, scale, FontWeight.Medium),
    headlineMedium = materialBase.headlineMedium.scaled(30f, 42f, scale, FontWeight.Medium),
    headlineSmall = materialBase.headlineSmall.scaled(26f, 36f, scale, FontWeight.Medium),
    titleLarge = materialBase.titleLarge.scaled(24f, 34f, scale, FontWeight.Medium),
    titleMedium = materialBase.titleMedium.scaled(21f, 30f, scale, FontWeight.Medium),
    titleSmall = materialBase.titleSmall.scaled(19f, 27f, scale, FontWeight.Medium),
    bodyLarge = materialBase.bodyLarge.scaled(20f, 30f, scale),
    bodyMedium = materialBase.bodyMedium.scaled(18f, 27f, scale),
    bodySmall = materialBase.bodySmall.scaled(16f, 24f, scale),
    labelLarge = materialBase.labelLarge.scaled(20f, 26f, scale, FontWeight.Medium),
    labelMedium = materialBase.labelMedium.scaled(17f, 23f, scale),
    labelSmall = materialBase.labelSmall.scaled(15f, 21f, scale),
)
