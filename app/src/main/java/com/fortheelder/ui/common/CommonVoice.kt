package com.fortheelder.ui.common

/**
 * 跨页面共用的界面文案（NFR-17 的「集中常量」）。
 *
 * 落点为什么不是 `res/strings.xml`，见 `records/03-架构决策记录.md` 的 **ADR-017**：
 * 现有的禁用词机器扫描（`CompanionVoiceTest`、`TalkVoiceTest`、`PlayVoiceTest`）只扫 Kotlin，
 * 把文案搬进资源文件会让那层保护失效。规则本身与文字去向无关，只要求「不散落硬编码」。
 *
 * 这里只放**不止一页在用**的句子。只有一页用的写在那个包自己的 `*Voice.kt` 里，
 * 免得这一份长成第二个大杂烩。
 *
 * 摆法有两条自我约束：
 * 1. **同一个意思只写一次**。首页与引导第一屏的表头都是「常伴」，清空确认弹窗在设置页
 *    出现两次、对话页出现一次，三处措辞必须逐字一致——否则用户会以为那是两件事。
 * 2. **一个词都不改**。这里的每一条都是从界面文件里原样搬出来的，设备用例
 *    （`androidTest`）里有按原文断言的用例，改一个标点就会红。
 */
object CommonVoice {

    /** 应用名。首页表头与引导第一屏共用。 */
    const val appTitle: String = "常伴"

    /** 返回按钮的无障碍说明。它是图标按钮，没有可见文字，只能靠这一句。 */
    const val back: String = "返回上一页"

    /** 称呼输入框的占位提示。引导第二页与设置页问的是同一件事，用同一句话。 */
    const val addressPlaceholder: String = "比如：老张、王老师"

    /** 「怎么称呼您」这个小标题。引导第二页的表头与设置页的区块标题用的是同一句。 */
    const val addressQuestion: String = "怎么称呼您"

    /** 「字的大小」这个小标题。引导第二页与设置页都有一处，措辞必须一致。 */
    const val fontSection: String = "字的大小"

    /** 不可撤销操作的确认弹窗标题。清空设置 / 清空作息 / 清空记住的事，三处共用。 */
    const val confirmClearTitle: String = "确定要清空吗？"

    /** 弹窗上的确认按钮。 */
    const val confirmClear: String = "清空"

    /** 弹窗上的退路。措辞刻意口语化——「取消」在老人的经验里常和「撤销」混。 */
    const val dismissClear: String = "不清了"
}
