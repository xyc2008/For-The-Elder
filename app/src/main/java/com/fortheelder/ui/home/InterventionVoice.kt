package com.fortheelder.ui.home

import com.fortheelder.domain.engine.greeting.Greeting
import com.fortheelder.domain.engine.intervention.InterventionVerdict
import com.fortheelder.domain.engine.intervention.QuietReason
import com.fortheelder.domain.engine.intervention.RhythmBasis

/**
 * 首页顶部那张卡上要说的话。
 *
 * 只有两句：一句主话、一句补充。跟 [Greeting] 的形状一致，界面因此不需要知道
 * 「这次是问候还是安静语」——换的只是字。
 */
data class Opening(val headline: String, val note: String)

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

    fun opening(verdict: InterventionVerdict, greeting: Greeting): Opening = when (verdict) {
        is InterventionVerdict.Speak -> when (verdict.basis) {
            // 按用户自己的作息说话，问候原样端上去：它本来就说的是此刻该说的话。
            RhythmBasis.LEARNED -> Opening(headline = greeting.headline, note = greeting.note)

            // 「认识多少天」这类事实照旧保留，只是在后面补一句说清按什么作息。
            RhythmBasis.ASSUMED_NOT_ENOUGH_DATA,
            RhythmBasis.ASSUMED_INCONSISTENT,
            -> Opening(
                headline = greeting.headline,
                note = "${greeting.note}说话的时机，我先按普通的作息来。",
            )
        }

        is InterventionVerdict.StayQuiet -> when (verdict.reason) {
            // 用户平常已经睡了。不解释算法，只说明此刻为什么没迎上来，并留一个出口：
            // 他想说话随时可以说话——安静不等于把应用关掉。
            QuietReason.SLEEP_HOURS -> Opening(
                headline = "这会儿是您休息的时间。",
                note = "我就不迎上来说话了，安静陪着您。您要是想说什么，随时说。",
            )

            // 刚打过招呼。同样不说「上次」这类记账式的词，免得像在催。
            QuietReason.TOO_SOON -> Opening(
                headline = "先不重复招呼了。",
                note = "我在这儿。想说话随时说。",
            )
        }
    }
}
