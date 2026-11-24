package com.fortheelder.ui.home

import com.fortheelder.domain.companion.Anchor
import com.fortheelder.domain.companion.CompanionVoice
import com.fortheelder.domain.engine.greeting.Greeting
import com.fortheelder.domain.engine.intervention.InterventionVerdict
import com.fortheelder.domain.engine.intervention.QuietReason
import com.fortheelder.domain.engine.intervention.RhythmBasis
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit

/**
 * 首页顶部那张卡上要说的话。
 *
 * 一句主话，下面最多两行补充。跟 [Greeting] 的形状一致，界面因此不需要知道
 * 「这次是问候还是安静语」——换的只是字。
 */
data class Opening(
    val headline: String,
    val note: String,
    /**
     * 顶上那两句话下面的一行补充，说一件用户自己记下的小事。没有就是 `null`。
     *
     * 只有真的迎上来说话（[InterventionVerdict.Speak]）时才可能带上它：判定为安静的时候，
     * 多说一句「您记下的那件事」就等于换个法子把话说出来了，那是把安静语变成了一半的招呼。
     */
    val anchorLine: String? = null,
    /**
     * 安静的时候，[note] 下面的一行补充，说「最早什么时候会再迎上来说话」。没有就是 `null`。
     *
     * 只出现在 [QuietReason.SLEEP_HOURS] 与 [QuietReason.TOO_SOON] 两支上。
     * [QuietReason.USER_ASKED] 那一支刻意不给（ADR-055）：用户自己说了今天先安静一会儿，
     * 这时候再报一个时刻，等于把他那句话读成一次待办；而且判定给它的
     * [StayQuiet.earliestNextAt] 是明天零点，本来也不是一句真话。
     *
     * 与 [anchorLine] 一样，这一行**只属于安静那一支**，说话时必须是 `null`：
     * 正在跟你打招呼的卡片底下挂一句「下一次最早什么时候」，等于把这次招呼说成了例行公事。
     */
    val earliestLine: String? = null,
)

/**
 * 把判定结论变成话。
 *
 * 判定是「该不该说话」，这里是「说什么」，分开的理由跟别处一样：要是判定里顺手写文案，
 * 想改一句措辞就得动判定逻辑，而判定是被穷举测试守着的。
 *
 * ## 措辞上的两条自我约束
 *
 * 1. **不能承诺这个版本做不到的事**。本版本没有通知通道，所以安静语只说
 *    「我不迎上来说话」「先不重复招呼」，绝不出现「到点提醒您」这类句子。
 * 2. **没有画像时不能暗示在学用户**。采集开关可能是关着的（AC-04.4 默认关），
 *    这种时候说「还在摸您的作息」就是空头支票。所以只说事实：
 *    「我先按普通的作息来」。
 */
object InterventionVoice {

    /**
     * 这次要说的话。
     *
     * [anchor] 是用户自己记下的一件小事，可以为空。**它只在 [InterventionVerdict.Speak] 时
     * 才可能出现在话里**：判定为安静的时候还多补一句「您记下的那件事」，等于换个法子把话说出来了，
     * 安静语就不再是安静语。所以下面安静那一支压根不读这个参数。
     *
     * [now] 是判定那一刻的钟点。**刻意不给默认值**：安静的时候要靠它算出「最早什么时候会再
     * 迎上来说话」，给一个默认值只会让将来漏传参数时安静地少掉那一行，没有任何测试会红。
     * 写成必填，编译期就把「拿一个跟判定不同的时间来说话」挡在门外。
     */
    fun opening(
        verdict: InterventionVerdict,
        greeting: Greeting,
        now: LocalDateTime,
        anchor: Anchor? = null,
    ): Opening = when (verdict) {
        is InterventionVerdict.Speak -> when (verdict.basis) {
            // 按用户自己的作息说话，问候原样端上去：它本来就说的是此刻该说的话。
            RhythmBasis.LEARNED -> Opening(
                headline = greeting.headline,
                note = greeting.note,
                anchorLine = anchor?.let { CompanionVoice.dueLine(it) },
            )

            // 「认识多少天」这类事实照旧保留，只是在后面补一句说清按什么作息。
            RhythmBasis.ASSUMED_NOT_ENOUGH_DATA,
            RhythmBasis.ASSUMED_INCONSISTENT,
            RhythmBasis.ASSUMED_COLLECT_OFF,
            -> Opening(
                headline = greeting.headline,
                note = "${greeting.note}说话的时机，我先按普通的作息来。",
                anchorLine = anchor?.let { CompanionVoice.dueLine(it) },
            )
        }

        is InterventionVerdict.StayQuiet -> when (verdict.reason) {
            // 用户自己说的。这里要把「这是您说的」讲清楚：不能让他觉得是应用自作主张
            // 把他静音了，也不能像在跟他赌气。同样留一个出口：安静的是招呼，不是这个应用。
            //
            // 这一支**刻意不给** [Opening.earliestLine]（ADR-055）：用户自己说了今天先安静一会儿，
            // 再报一个时刻，等于把他那句话读成一次待办；何况判定塞给它的
            // [InterventionVerdict.StayQuiet.earliestNextAt] 是明天零点，本来就不是一句真话。
            QuietReason.USER_ASKED -> Opening(
                headline = "好，那就先安静一会儿。",
                note = "您说过今天先安静一会儿。我就不迎上来说话了，想说什么，随时说。",
            )

            // 用户平常已经睡了。不解释算法，只说明此刻为什么没迎上来，并留一个出口：
            // 他想说话随时可以说话——安静不等于把应用关掉。
            QuietReason.SLEEP_HOURS -> Opening(
                headline = "这会儿是您休息的时间。",
                note = "我就不迎上来说话了，安静陪着您。您要是想说什么，随时说。",
                earliestLine = earliestLineOf(verdict.earliestNextAt, now),
            )

            // 刚打过招呼。同样不说「上次」这类记账式的词，免得像在催。
            QuietReason.TOO_SOON -> Opening(
                headline = "先不重复招呼了。",
                note = "我在这儿。想说话随时说。",
                earliestLine = earliestLineOf(verdict.earliestNextAt, now),
            )
        }
    }

    /**
     * 安静语下面那一行：最早什么时候会再迎上来说话。
     *
     * 只说钟点，不出现「提醒」「到点」这类词——本版本没有通知通道，写了就是承诺做不到的事
     * （见本文件开头第 1 条自我约束）。[InterventionVerdict.StayQuiet] 分给这一行的话也不许改口径。
     *
     * 只说得出口「今天」与「明天」两个日子：
     *
     * - [earliest] 为空、落在后天及以后、或者落在**过去**时，返回 `null`。后两种只在系统
     *   时钟被往回调时才会出现（走 [QuietReason.SLEEP_HOURS] 与 [QuietReason.TOO_SOON] 两支时
     *   它总是未来某一刻），与其报一个听着像真的时刻，不如这一行干脆不出现。
     * - 落在次日时前面加「明天 」：「07:00 之后」在深夜读起来分不清是今天早上还是明天早上。
     * - 时刻截到分：判定给的秒与纳秒对用户没有意义，印出来只会是噪声。
     */
    private fun earliestLineOf(earliest: LocalDateTime?, now: LocalDateTime): String? {
        val at = earliest ?: return null
        if (at.isBefore(now)) return null
        val days = ChronoUnit.DAYS.between(now.toLocalDate(), at.toLocalDate())
        if (days != 0L && days != 1L) return null
        val lead = if (days == 1L) "$EARLIEST_LEAD$EARLIEST_TOMORROW" else EARLIEST_LEAD
        val clock = at.toLocalTime().truncatedTo(ChronoUnit.MINUTES).toString()
        return "$lead $clock $EARLIEST_TAIL"
    }

    private const val EARLIEST_LEAD = "下一次迎来说话最早在"
    private const val EARLIEST_TAIL = "之后。"
    private const val EARLIEST_TOMORROW = "明天"
}
