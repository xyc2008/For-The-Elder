package com.fortheelder.ui.play

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
 * 3. **不承诺**。不说这个游戏对脑子有什么好处——那是给不出的承诺
 *    （AC-11.1，词表见 [com.fortheelder.domain.play.PlayWords]）。
 */
object PlayVoice {

    /** 首页那一行与这一页的表头用同一句。 */
    const val title = "玩一会儿"

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

    /** 现在该点哪个。这是唯一一条会被反复读到的信息，所以放在最上面、字号最大。 */
    fun nextTarget(round: PlayRound): String = "现在点 ${round.target}"

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
     * 只说两件真的发生的事：格子都点到了、这一局花了多久。没有评级、没有「比上次」，
     * 也没有任何「下次」——这一局之后没有下一局，除非用户自己回去重新进来。
     */
    fun doneLine(round: PlayRound, seconds: Int): String =
        "${round.total} 个都点到了。这一局用了 ${duration(seconds)}。"

    /**
     * 结束卡上那行小字。
     *
     * 说的是实话：用时只在屏幕上，这一页一关就没了。把它写出来，是因为用户有权利知道
     * 发生了什么——不说清，他可能会以为「不知道存哪儿去了」，进而以为被记了一笔。
     */
    const val doneNote = "用时只在这里显示，关掉就没了。"

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
