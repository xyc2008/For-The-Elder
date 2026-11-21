package com.fortheelder.ui.talk

import com.fortheelder.domain.engine.intent.IntentKind
import com.fortheelder.domain.wish.StoredWish
import com.fortheelder.domain.wish.WishDecision
import com.fortheelder.domain.wish.WishGate
import com.fortheelder.domain.wish.WishStatus

/**
 * 闸门上的话。
 *
 * 全部是**纯字符串函数**，所以能写在单元测试里：Tab 上的每一句话都可以被断言，
 * 不用起模拟器。这一点重要，因为这页上真正会被用户误会的不是布局，是用词——
 * 「常伴记住了」和「常伴帮您安排好了」在代码里只差几个字，对用户却是两件事。
 *
 * 三条自我约束：
 * 1. **不承诺没做出来的事**。确认之后给的内容只描述这个版本真的能做到的（记下来、
 *    显示在列表里、能删），不写「往后我会提醒您」这类第 9 轮才存在的行为。
 * 2. **不评价用户**。没有「很好」「加油」「坚持」这类词，也没有任何时间比较。
 * 3. **三类的用词分开**。睡不着和想学书法不是同一种事，对两者用同一句回应，
 *    用户会觉得它其实没听懂。
 */
object TalkVoice {

    /**
     * 还没表态时的问题。按类别分开措辞。
     *
     * 问句里**只出现「记不记」这一个决定**。第 7 轮那版 INTEREST 的措辞是
     * 「往后您提起来就顺着聊」，那是句做不到的话：这个 App 没有对话能力，
     * 用户真带着这件事回来说，得到的仍然只是「我听懂了什么」的回显。
     * 承诺一个不存在的功能，比少说一句话糟得多。
     */
    fun prompt(kind: IntentKind): String = when (kind) {
        IntentKind.INTEREST -> "这件事要不要常伴替您记着？"
        IntentKind.STATE -> "这件事要不要常伴放在心上？"
        IntentKind.REQUEST -> "这件事要不要常伴记下来？"
    }

    /** 同意按钮。INTEREST 用的「愿意了解一下」是 docs/01 AC-07.2 里点名的例子。 */
    fun confirmLabel(kind: IntentKind): String = when (kind) {
        IntentKind.INTEREST -> "愿意了解一下"
        IntentKind.STATE -> "好，放在心上"
        IntentKind.REQUEST -> "好，记下来"
    }

    /** 拒绝按钮。点它就等于明说「不用」。 */
    fun declineLabel(kind: IntentKind): String = when (kind) {
        IntentKind.INTEREST -> "先不用"
        IntentKind.STATE -> "先不用放心上"
        IntentKind.REQUEST -> "先不用记"
    }

    /**
     * 改主意的按钮。
     *
     * 只在用户**自己回到列表里**、主动点它时才出现，常伴不会拿这句话去主动提。
     * 没有这个入口的话，一次误点「先不用」就是永久生效，用户没有任何补救办法。
     */
    fun reopenLabel(kind: IntentKind): String = when (kind) {
        IntentKind.INTEREST -> "我现在想了解一下"
        IntentKind.STATE -> "现在可以放心上"
        IntentKind.REQUEST -> "现在可以记下来"
    }

    /**
     * 表过态之后的结果说明。这**不是**内容，只是一句如实的反馈。
     *
     * 三类的确认语都只讲**这件事本身的状态**，不预告任何后续动作：
     * 「您提起来的时候，常伴会接着听」说的是已有的听写能力，
     * 「不会拿这件事去别处说」说的是没有联网这个事实。
     */
    fun decidedLine(kind: IntentKind, decision: WishDecision): String = when (decision) {
        WishDecision.CONFIRM -> when (kind) {
            IntentKind.INTEREST -> "好，记下了。您提起来的时候，常伴会接着听。"
            IntentKind.STATE -> "好，放心上了。常伴不会拿这件事去别处说。"
            IntentKind.REQUEST -> "好，记下了。您什么时候想提，说一声就行。"
        }
        WishDecision.DECLINE -> when (kind) {
            IntentKind.INTEREST -> "好，这件事就先不聊了。"
            IntentKind.STATE -> "好，先不放在心上。"
            IntentKind.REQUEST -> "好，先不记了。"
        }
    }

    /**
     * 确认之后常伴能给出的东西。
     *
     * 只有 [WishGate.mayOffer] 放行（即 [WishStatus.CONFIRMED]）时才非空，
     * 别的一律是空列表——**AC-07.4 的断言就落在这个「空」上**：
     * 给定「意愿 + 未确认」，产出必须是空的。
     *
     * 内容只有一句，而且只讲这个版本真的做得到的事：它在清单里、能删。
     * 具体的内容推送（课程、场地）在 FR-08/FR-09，那一轮做到之前，
     * 这里绝不放一句「我帮您安排」来充场面。
     */
    fun offerLines(wish: StoredWish): List<String> =
        if (WishGate.mayOffer(wish.status)) {
            listOf("「${wish.term}」在下面「常伴记住的事」里，随时能删。")
        } else {
            emptyList()
        }

    /** 列表里每条意愿的状态说法。 */
    fun statusLabel(status: WishStatus): String = when (status) {
        WishStatus.PENDING -> "还没问过您"
        WishStatus.CONFIRMED -> "记住了"
        WishStatus.DECLINED -> "您说过先不用"
    }
}
