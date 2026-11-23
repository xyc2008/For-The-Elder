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
 *
 * ## 为什么还要一个「相邻目标之间的净间隙」
 *
 * 目标够大不等于点得准。同一篇 NN/g 综述把这件事单列成一小节「**Mind the Gap——Crowding
 * Causes Errors**」：堆叠起来的按钮或链接列表，出错的主要来源是彼此挨得太近，措辞是
 * 目标先要够大、**然后**还要留出足够的间距。手指落点不是一个点，是一个约 1.6–2cm 的接触面
 * （该文引 MIT Touch Lab 的测量），边缘相碰的目标会把这次触摸判给隔壁。
 *
 * 第 62 轮在设备上量到的正是这种情形：五句现成的话每一句的可点框高 168 px，相邻两句之间
 * **只有 21 px**——21 px 来自卡片内层的 `gapSm` = 8dp × 420dpi ÷ 160。[minTouchGap] 就是
 * 为这一类位置设的下限，第 63 轮起被 [TargetStack] 用在相邻可点栈上。
 *
 * 那篇文章**没有给间隙的数值下限**，所以 [minTouchGap] 的取值是本项目的决定，不是引文里的
 * 数字：取 16dp 的理由只有两条——必须明显大于实测太挤的 8dp，且不必大过目标本身
 * （16dp ÷ 64dp = 1/4）。按 160dpi 名义密度换算约 4.7mm。
 *
 * [primaryActionHeight] 是主操作按钮的高度，比次要按钮的 [buttonHeight] 高一档。
 * 同一篇综述的说法是 `primary calls to action often deserve greater visual prominence and
 * thus deserve a larger tap target`——主次不只靠填色和描边区分，尺寸也分层。
 * 72dp ≈ 1.14cm，仍在这条 1cm 线之上。
 */
object ElderDimens {
    val minTouchTarget = 64.dp
    val buttonHeight = 64.dp
    val primaryActionHeight = 72.dp
    val entryCardMinHeight = 112.dp

    /** 相邻可点元素之间的最小净间隙。理由见类注释「Mind the Gap」那一节。 */
    val minTouchGap = 16.dp

    val gapXs = 4.dp
    val gapSm = 8.dp
    val gapMd = 16.dp
    val gapLg = 24.dp
    val gapXl = 32.dp

    val screenPadding = 20.dp
    val cardPadding = 20.dp
}
