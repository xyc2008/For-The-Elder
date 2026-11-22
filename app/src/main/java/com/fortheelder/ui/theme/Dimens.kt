package com.fortheelder.ui.theme

import androidx.compose.ui.unit.dp

/**
 * 尺寸常量。
 *
 * [minTouchTarget] 是一个**硬性下限**：55 岁以上人群的手部精细动作与触觉反馈都在下降，
 * 48dp（Material 默认）在实测中对这个人群偏小，常用的误触现象是「想点 A 结果点到 B」。
 * 所有可点击元素——按钮、列表项、开关、游戏格子——都必须至少这么高。
 *
 * ## 为什么是 64dp
 *
 * 依据不是「感觉够大」，而是触摸目标的最小**物理**尺寸。Nielsen Norman Group 的综述
 * 《Touch Targets on Touchscreens》给出的实证结论是可准确点中的目标不小于 1cm × 1cm，
 * 同一篇引 MIT Touch Lab 的测量：成人指尖接触面平均 1.6–2cm、拇指 2.5cm。
 *
 * 触控目标是物理量，所以换算口径必须写清楚：本项目的换算按 **160dpi 名义密度**做，
 * 即 1in = 160dp，于是 1cm ≈ 63.0dp。取整到 64dp ≈ 1.02cm，过线；
 * 上一版的 56dp ≈ 8.9mm，**不过线**。
 *
 * 这条口径的局限照实说：真实设备的 dpi 各不相同，1dp 的实际物理长度会有几个百分点的出入。
 * 上一条换算给的是「按 Android 的名义密度算」的结论，不是拿尺子量出来的。
 *
 * [entryCardMinHeight] 是首页两个主入口的高度。最小下限只回答「点不点得中」，
 * 主入口该比下限宽裕得多——同一篇综述里 Target 就把两个主功能做成了约 2cm × 2cm。
 */
object ElderDimens {
    val minTouchTarget = 64.dp
    val buttonHeight = 64.dp
    val entryCardMinHeight = 112.dp

    val gapXs = 4.dp
    val gapSm = 8.dp
    val gapMd = 16.dp
    val gapLg = 24.dp
    val gapXl = 32.dp

    val screenPadding = 20.dp
    val cardPadding = 20.dp
}
