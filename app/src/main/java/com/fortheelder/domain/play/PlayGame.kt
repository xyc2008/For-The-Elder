package com.fortheelder.domain.play

import kotlin.random.Random

/**
 * 点错了之后给的那句话。
 *
 * 做成枚举而不是散在 `when` 里的字符串，是为了让测试能**穷举**：枚举的每个取值都会被
 * 词表扫一遍（用例在 `PlayWordsTest` 里），而散落的字面量只能抽查。这一块的特殊之处在于
 * 用户最容易误会的就是用词——「这个已经点过了」和「你又点错了」在代码里只差几个字，
 * 对用户却是两件事。
 *
 * 三个取值都只陈述事实，没有一个字在评价人。更要紧的是：
 * **这里没有任何一处能记下「错了几次」**——[PlayRound] 里也没有对应的字段，
 * 所以 AC-12.3 要的「不扣分、不计数」是结构上做不到，不是靠自觉。
 */
enum class PlayHint(val text: String) {
    /** 正在顺利往下走，没有话要说。 */
    NONE(""),

    /** 点了已经点过的那个。 */
    ALREADY_DONE("这个已经点过了，换一个"),

    /** 点了排在后面的那个。 */
    NOT_YET("这个还在后面，不急"),
}

/**
 * 一局进行到某一步的样子。
 *
 * 这是个**不可变值**：每点一下就返回新的一局，已经发生过的事没有人能改。
 *
 * 字段只有四个，而且是刻意挑出来的：
 * - 没有「错了几次」「扣了几分」「用了多久」——记错的字段一个都没有（AC-12.3），
 *   用时也不在这一层：它在界面上由 [com.fortheelder.core.time.AppClock] 现算，
 *   算完只活在一屏之内（AC-12.2、DR-09）。
 * - [step] 是已经点对的个数，下一个该点的是谁由它推出来（[target]），不另存一份：
 *   两个字段能互相矛盾的时候，界面迟早会显示出自相矛盾的东西。
 * - 没有 id、没有时间戳、没有用户字段，所以「这是上次那一局的第几步」这种话
 *   在这里根本无从表达——不落库不是纪律，是没有可以落的地方。
 *
 * [cells] 与 [order] 装的是同一批字，只是顺序不同：[cells] 是摆在格子上的位置，
 * [order] 是该点的先后。这一层不认识「数字」，只认识「相等」和「先后」，
 * 所以将来加一个用字词的小游戏不需要改这个类。
 */
data class PlayRound(
    val gameId: String,
    /** 格子上按位置摆好的字，长度就是格子数。 */
    val cells: List<String>,
    /** 该点的先后顺序。 */
    val order: List<String>,
    /** 已经点对了几个。 */
    val step: Int = 0,
    val hint: PlayHint = PlayHint.NONE,
) {
    /** 下一个该点的字。已经点完时是空串。 */
    val target: String get() = order.getOrElse(step) { "" }

    val total: Int get() = order.size

    val finished: Boolean get() = step >= order.size

    /**
     * 这一格是不是已经点过了。界面据此把它压暗，用户一眼看得出还剩哪些。
     *
     * 用 `indexOf` 现算而不是另存一张「已点集合」：多一份状态就多一处能对不上的地方。
     */
    fun passed(label: String): Boolean {
        val rank = order.indexOf(label)
        return rank >= 0 && rank < step
    }

    /**
     * 点了第 [index] 格。
     *
     * 点对：往前走一步，提示清空。**点错：除了换一句话，什么都不变**——[step] 不动、
     * [cells] 不动，也没有别的地方可以记下来。越界、或者已经点完时原样返回，
     * 界面不需要自己防。
     */
    fun tap(index: Int): PlayRound {
        if (finished) return this
        val label = cells.getOrNull(index) ?: return this
        if (label == target) return copy(step = step + 1, hint = PlayHint.NONE)
        val rank = order.indexOf(label)
        return copy(hint = if (rank in 0 until step) PlayHint.ALREADY_DONE else PlayHint.NOT_YET)
    }
}

/**
 * 一个能玩的小游戏。
 *
 * 刻意做成**数据类 + 一个出局函数**，而不是 `interface PlayGame`：这个版本只有
 * 一个游戏，为它单开一个接口就落进 docs/02 约束 C-04 说的「只有一个实现的接口」——
 * 接口什么都没抽象掉，只是让每个调用点多一次跳转。
 *
 * 做成数据之后，加第二个游戏仍然是往 [PlayCatalog.games] 里再写一条 `PlayGame(...)`，
 * 领域层与界面一行都不用改；这跟 [com.fortheelder.domain.companion.StarterCatalog]
 * 是同一个形状，两处不用各活一套规矩。
 *
 * 这个类里没有等级、没有难度、没有排行榜字段，也没有任何形式的达标线：
 * [longestSeconds] 是**上限**，不是目标，更不是及格线。
 */
data class PlayGame(
    val id: String,
    val name: String,
    /** 一句话说明怎么玩。会被词表扫描，见 [PlayWords]。 */
    val howTo: String,
    /**
     * 格子一行摆几个。
     *
     * 摆法是游戏自己的事，界面不假定它是 5×5。将来若有游戏不是方阵，
     * 改的是那条目录项，界面一行都不用动。
     */
    val columns: Int,
    /**
     * 这一局最多花多久（秒）。
     *
     * AC-11.3 的上限是 3 分钟。这既是界面上那句说明的出处，也是测试断言的对象——
     * 不是随便填的文案。它**不做倒计时**：屏幕上跳动的秒数本身就是催促，
     * 而这个游戏的全部意义只是「手上有件事做」。
     */
    val longestSeconds: Int,
    /** 出一局新的。纯函数：同一个 [Random] 出同一局，所以测试能造出确定的局面。 */
    val deal: (Random) -> PlayRound,
)
