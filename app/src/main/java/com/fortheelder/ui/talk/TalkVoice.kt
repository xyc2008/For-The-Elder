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

    // ------------------------------------------------------------------
    // 页面骨架上的固定文案（NFR-17 的「集中常量」，取舍见 ADR-017）
    //
    // 上半部分是与状态有关的措辞，下面这一部分是不随任何状态变化的标题与说明。
    // 两者放在同一个对象里，是因为它们属于同一页：读这一份文件就应该能读完整页的口气，
    // 而不是在半页之后跳到另一个文件继续读。
    // ------------------------------------------------------------------

    const val pageTitle: String = "说两句"

    const val introLine: String = "您可以把想说的写下来。一时想不出说什么，页面底下有几句话可以点着试试。"

    const val introNote: String =
        "常伴只听得懂它学过的那几种说法，也没有联网。听不明白的时候它会直接说听不明白，不会瞎猜。"

    const val saySection: String = "说一句"
    const val draftLabel: String = "比如：我一直想学书法"
    const val submit: String = "说完了"
    const val submitNote: String = "说完常伴会把听懂的东西念给您听，问一句要不要记。"

    const val examplesSection: String = "不知道说什么，点一句试试"

    const val heardSection: String = "我听见的"

    const val notUnderstoodSection: String = "这句我没听明白"
    const val notUnderstoodLine: String = "没关系，换个说法也行，或者点下面别的句子。"
    const val notUnderstoodNote: String = "没听懂的时候，常伴不会随便找个话题接着聊。"

    /** 「从哪几个字听出来的」。这是判定依据，不是解释——所以只报词，不报分数。 */
    fun evidenceLine(evidence: String): String = "是从「$evidence」这几个字听出来的。"

    const val gateNote: String = "您点上面那两个按钮之前，常伴不会拿这件事给您推任何东西。"
    const val reopenNote: String = "您要是改主意了，点下面这个按钮就行。"

    const val rememberedSection: String = "常伴记住的事"
    const val rememberedEmpty: String = "现在还是空的。"
    const val rememberedEmptyNote: String = "您说过的、常伴听懂的，会出现在这里。一件都不留也可以。"
    const val rememberedNote: String = "常伴只记这几件事，都在这台手机里，您随时能删。"
    const val pendingSection: String = "还没问过您的"
    const val pendingNote: String = "这几件常伴还没问过您，等您拿主意。"
    const val clearAll: String = "全部清空"
    const val clearAllConfirmText: String = "常伴记住的事会全部删掉。这一步不能撤销。"
    const val forgetThis: String = "这件事不用记了"

    /** 把用户刚说的那句话原样念一遍，让他知道在对哪一句作答。 */
    fun echoLine(sentence: String): String = "您说的是：「$sentence」"

    /** 起点卡展开 / 收起的图标说明。图标没有可见文字，这两句就是它的全部含义。 */
    const val expandStep: String = "展开这一步"
    const val collapseStep: String = "收起这一步"

    /**
     * 类别的中文说法。
     *
     * 用「哪一类」而不是「百分比置信度」：这是词表匹配，没有概率可言，报一个 0.87 是编的。
     */
    fun kindLabel(kind: IntentKind): String = when (kind) {
        IntentKind.INTEREST -> "想做的事"
        IntentKind.STATE -> "现在的状态"
        IntentKind.REQUEST -> "想找人帮的事"
    }
}
