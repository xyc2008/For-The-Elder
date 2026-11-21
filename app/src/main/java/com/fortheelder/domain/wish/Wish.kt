package com.fortheelder.domain.wish

import com.fortheelder.domain.engine.intent.IntentKind

/**
 * 一条意愿的确认状态。
 *
 * 三态而不是「记住 / 不在乎」两态：界面上要区分「还没问过」（该问一次）和
 * 「问过了，人家说了不用」（不许再问），把这两种情况压成一个 `Boolean` 就会出现
 * 反复追问——那正是 docs/01 的 AC-07.3 要挡掉的行为。
 */
enum class WishStatus {
    /** 听出来了，用户还没表态。**此状态下不得推出任何内容**（AC-07.1）。 */
    PENDING,

    /** 用户明确点了同意。只有这个状态才允许给内容。 */
    CONFIRMED,

    /** 用户明确说了不用。**常伴不得再主动提起**（AC-07.3）。 */
    DECLINED,
}

/**
 * 用户在闸门上能做的两个动作。
 *
 * 只有两个，没有第三个——**「继续往下聊」不算表态**（AC-07.2）。用户点了一句示例话、
 * 或者又说了别的，都只是说话，不能顺带被当成同意。
 */
enum class WishDecision { CONFIRM, DECLINE }

/**
 * 常伴记住的一条意愿（DR-07）。
 *
 * [evidence] 与 [sentence] 是**用户原话**：`sentence` 是当时整句，`evidence` 是判定的
 * 逐字片段。存下来是为了在「常伴记住的事」里能回答「它是从哪儿听出这件事的」——
 * 答不出这个问题，用户就没法判断它记得对不对。
 *
 * 刻意**不存**「最近一次提到是什么时候」，界面上也不显示任何时间：一旦显示
 * 「上次说的时候是三个月前」，这个 App 就从陪伴变成了评价。
 *
 * [decidedAtEpochMilli] 只在用户真的表过态之后才有值。
 */
data class StoredWish(
    val kind: IntentKind,
    val term: String,
    val evidence: String,
    val sentence: String,
    val status: WishStatus,
    val heardAtEpochMilli: Long,
    val decidedAtEpochMilli: Long?,
)

/**
 * 意愿确认闸门（FR-07）。
 *
 * 这里是**纯状态判断**：不碰数据库、不碰识别引擎、不碰界面。AC-07.4 明确要求闸门
 * 独立于 NLU 且能单独单测「给定意愿 + 未确认，断言产出为空」，所以判断必须是一个
 * 能对三种状态穷举的函数，而不是散在 Composable 里的 `if`。
 *
 * 这个 `object` 的方法全是纯函数，因此也不进 `AppContainer`。
 */
object WishGate {

    /**
     * 能不能给内容。
     *
     * 只有 [WishStatus.CONFIRMED] 放行。**未确认与已拒绝一律不放行**——已拒绝也
     * 不放行，是因为「不用」不等于「不要内容」，但用户既然是拒绝这个意愿本身，
     * 顺手拿它去推东西就更没道理。
     */
    fun mayOffer(status: WishStatus): Boolean = status == WishStatus.CONFIRMED

    /**
     * 能不能主动提起这件事。
     *
     * 只有还没表态的（[WishStatus.PENDING]）可以问一次。已确认的不必再问，
     * 已拒绝的**不许**再问（AC-07.3）。
     */
    fun mayBringUp(status: WishStatus): Boolean = status == WishStatus.PENDING

    /** [wishes] 里还能主动提起的那些，顺序不变。 */
    fun bringUpable(wishes: List<StoredWish>): List<StoredWish> = wishes.filter { mayBringUp(it.status) }

    /**
     * 一次表态之后的**新状态**；状态不变时返回 `null`。
     *
     * 返回 `null` 而不是原状态，是为了让调用方能把「什么都没发生」和「状态真的换了」
     * 分开：重复点同一个按钮不该去覆盖 [StoredWish.decidedAtEpochMilli]，
     * 那个时刻记录的是**第一次表态的时间**，反复刷新它就成了另一个意思。
     *
     * `DECLINED + CONFIRM → CONFIRMED` 是刻意的：用户自己改主意是一个**显式动作**
     * （界面上是一个写明了的按钮，不是常伴主动提起来的），AC-07.3 挡的是后者。
     * 不允许改主意的话，一次误点「先不用」就等于永久失去这条意愿，用户没有任何补救办法。
     */
    fun decide(from: WishStatus, decision: WishDecision): WishStatus? = when (decision) {
        WishDecision.CONFIRM -> if (from == WishStatus.CONFIRMED) null else WishStatus.CONFIRMED
        WishDecision.DECLINE -> if (from == WishStatus.DECLINED) null else WishStatus.DECLINED
    }
}
