package com.fortheelder.ui.play

import com.fortheelder.domain.play.FindPairRound
import com.fortheelder.domain.play.OrderRound
import com.fortheelder.domain.play.PlayGame
import com.fortheelder.domain.play.PlayRound
import com.fortheelder.domain.play.SubtractRound

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
 *    （AC-11.1，词表见 [com.fortheelder.domain.voice.ClaimWords]）；
 *    也**不给一局多久打保票**（第 22 轮，见 [promise]）。
 *
 * 从第 12 轮起有三个游戏以外的入口文案，并且按**规则**分支的地方用 `when` 而不是
 * `if`：新加一种规则时编译器会在这里报出漏掉的分支，不会悄悄沿用上一个游戏的措辞。
 * 第 17 轮加第三种规则时这句话第一次真的兑现了——编译当场报出三条漏掉的分支
 * （[headline]、[doneLine]、[doneNote]），补完才编得过。
 */
object PlayVoice {

    /** 首页那一行与这一页的表头用同一句。 */
    const val title = "玩一会儿"

    /** 有两个以上游戏时那一页的标题。只有一个游戏时它不出现。 */
    const val chooseTitle = "挑一个"

    /** 选择页底下那行小字。规则留到进去之后说，这里不重复。 */
    const val chooseNote = "点一个进去，里面会说怎么玩。"

    const val startLabel = "开始"

    /**
     * 规则页上的第二条路：这一个不想玩，回去挑别的（第 23 轮补）。
     *
     * 它不在 AC-11.3 的射程里：那条禁的是**同一局**的续玩邀请，而这里往回走的是「还没开始」
     * 这一步——这一局压根还没摆出来（见 [PlayViewModel.chooseAgain]），退回去什么都不损失。
     * 措辞仍然避开催促词表（`PlayVoiceTest.noLinePushesTheUserToKeepPlaying`）。
     */
    const val switchLabel = "换一个游戏"

    /** 结束卡上唯一的按钮。刻意**不是**「再来一局」：AC-11.3 明禁诱导续玩。 */
    const val leaveLabel = "回去歇会儿"

    /** 说明怎么玩。玩法本身写在游戏里（[PlayGame.howTo]），这一层不另编一套。 */
    fun rule(game: PlayGame): String = game.howTo

    /**
     * 开始之前那两句定心的话。
     *
     * 三句话都要站得住才配写在屏幕上：
     *
     * 1. 「大概 N 分钟」——N 由 [PlayGame.longestSeconds] 折算而来（[minutes]），不是手写死的。
     *    **说「大概」而不是「最多」是第 22 轮改的**：这一局没有东西会在超时后把它停下
     *    （`domain/play` 整层不认识时钟，守卫见 `PlayStaysClocklessTest`），写「最多」就是一句
     *    兑现不了的话——用户慢慢点，那句话就从说明变成他头上的一道催促。
     *    同一个仓库里「最多一天一次」站得住，因为它旁边有 `maxGapMinutes = 1440` 兜着
     *    （`docs/09` 第 7 节）；「最多 3 分钟」旁边什么都没有。
     * 2. 「不用计时」——界面上真的不存在计时器：这一页没有 `viewModelScope`、没有倒计时。
     * 3. 「没有分数跟谁比」——库里没有一列可以放成绩，这一局也不属于任何存储
     *    （AC-11.2、DR-09）。
     *
     * 末句「慢一点也不要紧」是把第 1 条的意思说白了：一局多久由用户说了算。
     * 三句都是能被验证的事实，不是安慰。
     */
    fun promise(game: PlayGame): String =
        "一局大概 ${minutes(game)} 分钟就完了。不用计时，也没有分数跟谁比；慢一点也不要紧。"

    /**
     * 屏幕最上面、字号最大的那一句。
     *
     * 舒尔特要说「该点哪个」，因为它的全部规矩就是顺序；找一样没有顺序可讲，
     * 反复说「现在点 3」反而是错的话。所以这里按规则分支，而不是共用一个句式。
     */
    fun headline(round: PlayRound): String = when (round) {
        is OrderRound -> "现在点 ${round.target}"
        is FindPairRound -> "找出一样的那两张"
        is SubtractRound -> "${round.current} 减 ${round.deduction} 是多少？"
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
     * 只说真的发生的事。舒尔特说「都点到了」和这一局花了多久；找一样的和算一算**都不说用时**——
     * 那两局根本没在计时，报一个数就是把不存在的东西说出来（算一算尤其不能报：那会变成一句
     * 「算五道用了多久」的心算测评，AC-11.1 明禁说出口）。三个游戏都没有评级、没有「比上次」，
     * 也没有任何「下次」：这一局之后没有下一局，除非用户自己回去重新进来。
     */
    fun doneLine(round: PlayRound, seconds: Int): String = when (round) {
        is OrderRound -> "${round.stepsToFinish} 个都点到了。这一局用了 ${duration(seconds)}。"
        is FindPairRound -> "那两张「${round.pairLabel}」都找到了。"
        is SubtractRound -> "${round.stepsToFinish} 道都算好了。"
    }

    /**
     * 结束卡上那行小字。
     *
     * 说的是实话：这一局的全部状态只在屏幕上，这一页一关就没了。把它写出来，是因为
     * 用户有权利知道发生了什么——不说清，他可能会以为「不知道存哪儿去了」，进而
     * 以为被记了一笔（DR-09）。找一样的和算一算留下的是同一个事实，所以共用一句。
     */
    fun doneNote(round: PlayRound): String = when (round) {
        is OrderRound -> "用时只在这里显示，关掉就没了。"
        is FindPairRound, is SubtractRound -> "这一局只在屏幕上，关掉就没了。"
    }

    /** 「一局大概几分钟」。向上取整，宁可说多不说少。它是估时，不是会中断这一局的期限（[promise]）。 */
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
