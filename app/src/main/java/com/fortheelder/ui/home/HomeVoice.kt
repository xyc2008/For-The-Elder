package com.fortheelder.ui.home

import com.fortheelder.ui.play.PlayVoice

/**
 * 首页上写死的那些话（NFR-17 的「集中常量」，取舍见 ADR-017）。
 *
 * 首页只有一句话是动态的——顶上那张卡片里的问候语，它来自
 * [com.fortheelder.domain.companion.GreetingEngine]，不在这里。这里放的是三个入口的
 * 标题、按钮文字，以及底下那句安静语：它们不随任何状态变化，却决定了这一页看上去像什么。
 *
 * 三组入口的用词是刻意分成两种口气的：**「想说话的时候」「手上想有点事的时候」说的是
 * 用户此刻的状态**，而按钮文字（「跟常伴说两句」「玩一会儿」）是那个状态下的具体动作。
 * 只写动作（「对话」「游戏」）会变成菜单，只写状态又点不动。
 */
object HomeVoice {

    /** 称呼还没填时出现在问候语下面的一行入口。它不是错误提示，是一个可以不管的建议。 */
    const val askAddress: String = "告诉我您希望被怎么称呼"

    const val sectionTalk: String = "想说话的时候"
    const val talkAction: String = "跟常伴说两句"

    const val sectionPlay: String = "手上想有点事的时候"

    /**
     * 游戏入口的文字**就是游戏页的标题**（[PlayVoice.title]），这里不再另写一遍。
     *
     * 第 36 轮之前两处各写了一遍同一个字面量，取值相同却没有哪条用例把两者钉在一起——
     * 谁改了一处，另一处会静静留在旧措辞上（`docs/03` 第 8 节的 GAP-04）。现在这里只是
     * **指向**那边那一个常量，全项目这个字面量只剩一处；`HomeVoiceTest` 里那条扫源码的
     * 用例和 [PlayVoice.title] 的相等断言各盯着一半。
     */
    const val playAction: String = PlayVoice.title

    const val sectionSettings: String = "您自己的节奏"
    const val settingsAction: String = "设置"

    /**
     * 页面末尾的安静语。
     *
     * 这一句是这一版最重要的一句话：它明确说「没有什么必须做的事」。删掉它，
     * 首页剩下的三张卡就变回了待办清单——而刚退休的人最不缺的就是被人排满的日程。
     */
    const val closingNote: String =
        "今天没有要做的事，也没有要签到的项目。想到就打开看看，想不起来也不会怎样。"
}
