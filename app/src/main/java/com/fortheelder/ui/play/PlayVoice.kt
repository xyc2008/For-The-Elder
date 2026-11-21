package com.fortheelder.ui.play

import com.fortheelder.domain.play.FindPairRound
import com.fortheelder.domain.play.OrderRound
import com.fortheelder.domain.play.PlayGame
import com.fortheelder.domain.play.PlayRound

/**
 * 这一页上所有的话。
 *
 * 全是**纯字符串函数**，所以能写在单元测试里：这一页真正会被用户误会的不是布局，是用词。
 * 「现在点 7」和「你怎么还没点到 7」在代码里只差几个字，对用户却是两件事——
 * 而这个应用的全部立场就是绝不站到后者那一边。
 *
 * 三条自我约束：
 * 1. **不评价**。没有「很好」「真快」「厉害」，也没有任何跟别人、跟上次的比较
 *    （AC-11.2）。这一页上不存在第二名，所以也没有名次。
 * 2. **不催**。没有倒计时，没有「还剩几个」，也没有「再来一局」（AC-11.3）。
 *    点完就只有一句结果和一条退路。
 * 3. **不承诺**。不说这些游戏对脑子有什么好处——那是给不出的承诺
 *    （AC-11.1，词表见 [com.fortheelder.domain.voice.ClaimWords]）。
 *
 * 从第 12 轮起有三个游戏以外的入口文案，并且按**规则**分支的地方用 `when` 而不是
 * `if`：新加一种规则时编译器会在这里报出漏掉的分支，不会悄悄沿用上一个游戏的措辞。
 */
object PlayVoice {

    /** 首页那一行与这一页的表头用同一句。 */
    const val title = "玩一会儿"

    /** 有两个以上游戏时那一页的标题。只有一个游戏时它不出现。 */
    const val chooseTitle = "挑一个"

    /** 选择页底下那行小字。规则留到进去之后说，这里不重复。 */
    const val chooseNote = "点一个进去，里面会说怎么玩。"

    const val startLabel = "开始"

    /** 结束卡上唯一的按钮。刻意**不是**「再来一局」：AC-11.3 明禁诱导续玩。 */
    const val leaveLabel = "回去歇会儿"

    /** 说明怎么玩。玩法本身写在游戏里（[PlayGame.howTo]），这一层不另编一套。 */
    fun rule(game: PlayGame): String = game.howTo

    /**
     * 开始之前那两句定心的话。
     *
     * 「没有分数跟谁比」不是修饰：库里没有一列可以放成绩，这一局也不属于任何存储，
     * 所以这句话是能被验证的事实，不是安慰（AC-11.2、DR-09）。
     */
    fun promise(game: PlayGame): String =
        "一局最多 ${minutes(game)} 分钟。不用计时，也没有分数跟谁比。"

    /**
     * 屏幕最上面、字号最大的那一句。
     *
     * 舒尔特要说「该点哪个」，因为它的全部规矩就是顺序；找一样没有顺序可讲，
     * 反复说「现在点 3」反而是错的话。所以这里按规则分支，而不是共用一个句式。
     */
    fun headline(round: PlayRound): String = when (round) {
        is OrderRound -> "现在点 ${round.target}"
        is FindPairRound -> "找出一样的那两张"
    }

    /**
     * 点错之后的那一句话；没有话说时返回 `null`。
     *
     * 措辞本身在 [com.fortheelder.domain.play.PlayHint] 里，这一层不重写——
     * 同一句话要被界面和测试同时用上时，只能有一个出处。
     */
    fun hint(round: PlayRound): String? = round.hint.text.ifBlank { null }

    /**
     * 点完之后的唯一一句结果。
     *
     * 只说真的发生的事。舒尔特说「都点到了」和这一局花了多久；找一样**不说用时**——
     * 它这一局根本没在计时，报一个数就是把不存在的东西说出来。两者都没有评级、
     * 没有「比上次」，也没有任何「下次」：这一局之后没有下一局，除非用户自己回去重新进来。
     */
    fun doneLine(round: PlayRound, seconds: Int): String = when (round) {
        is OrderRound -> "${round.stepsToFinish} 个都点到了。这一局用了 ${duration(seconds)}。"
        is FindPairRound -> "那两张「${round.pairLabel}」都找到了。"
    }

    /**
     * 结束卡上那行小字。
     *
     * 说的是实话：这一局的全部状态只在屏幕上，这一页一关就没了。把它写出来，是因为
     * 用户有权利知道发生了什么——不说清，他可能会以为「不知道存哪儿去了」，进而
     * 以为被记了一笔（DR-09）。
     */
    fun doneNote(round: PlayRound): String = when (round) {
        is OrderRound -> "用时只在这里显示，关掉就没了。"
        is FindPairRound -> "这一局只在屏幕上，关掉就没了。"
    }

    /** 「一局最多几分钟」。向上取整，宁可说多不说少。 */
    private fun minutes(game: PlayGame): Int = (game.longestSeconds + 59) / 60

    /** 用时的说法。整分钟不拖一个「0 秒」。 */
    fun duration(seconds: Int): String {
        val safe = if (seconds < 0) 0 else seconds
        val m = safe / 60
        val s = safe % 60
        return when {
            m == 0 -> "$s 秒"
            s == 0 -> "$m 分钟"
            else -> "$m 分 $s 秒"
        }
    }
}
