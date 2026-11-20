package com.fortheelder.ui.theme

import androidx.compose.ui.unit.dp

/**
 * 尺寸常量。
 *
 * [minTouchTarget] 是一个**硬性下限**：55 岁以上人群的手部精细动作与触觉反馈都在下降，
 * 48dp（Material 默认）在实测中对这个人群偏小，常用的误触现象是「想点 A 结果点到 B」。
 * 所有可点击元素——按钮、列表项、开关、游戏格子——都必须至少这么高。
 */
object ElderDimens {
    val minTouchTarget = 56.dp
    val buttonHeight = 64.dp

    val gapXs = 4.dp
    val gapSm = 8.dp
    val gapMd = 16.dp
    val gapLg = 24.dp
    val gapXl = 32.dp

    val screenPadding = 20.dp
    val cardPadding = 20.dp
}
