package com.fortheelder.domain.play

import kotlin.random.Random

/**
 * 点错了之后给的那句话。
 *
 * 做成枚举而不是散在 `when` 里的字符串，是为了让测试能**穷举**：枚举的每个取值都会被
 * 词表扫一遍（用例在 `ClaimWordsTest` 里），而散落的字面量只能抽查。这一块的特殊之处在于
 * 用户最容易误会的就是用词——「这个已经点过了」和「你又点错了」在代码里只差几个字，
 * 对用户却是两件事。
 *
 * 五个取值都只陈述事实，没有一个字在评价人。更要紧的是：
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

    /** 找一样的：点的那个不是要找的。 */
    NOT_THIS_ONE("不是它，再看看"),

    /**
     * 记一记：翻开的第二张和第一张不是一对，等着盖回去。
     *
     * 「点一下接着找」是操作指引而不是安慰话：这一对**不是自己消失的**，得由用户点一下
     * 才盖回去。为什么不让它自己盖回去，见 `records/03` ADR-038。
     */
    MISSED_PAIR("不是一对，点一下接着找"),
}

/**
 * 一格现在是什么样子。界面只认这三种，不认游戏规则。
 *
 * 之所以要有这个东西，是因为第二个游戏进来之后「压暗」的判据变了：舒尔特比的是
 * 「格子的字排在第几位、点过没有」，找一样比的是「这张牌是不是要找的那张字」。
 * 如果界面自己算，它就得同时懂两套规则——每加一个游戏都要动界面。改成由
 * [PlayRound.markOf] 说给界面听，界面就只剩「三种样子画成三种颜色」这一件事。
 *
 * 三种样子管的是**颜色**。格子上写什么字是另一件事，归 [PlayRound.faceOf] 管——
 * 记一记的牌背朝上时，格子上摆的字和该给用户看的字不是一回事（第 41 轮）。
 */
enum class CellMark {
    /** 平常的样子，还没碰过。 */
    PLAIN,

    /**
     * 已经点过一下、但还没成事（找一样里第一张选中的牌；记一记里翻开了还没成对的那两张）。
     */
    PICKED,

    /** 这一格已经了了。 */
    DONE,
}

/**
 * 一局进行到某一步的样子。
 *
 * 这是个**不可变值**：每点一下就返回新的一局，已经发生过的事没有人能改。
 *
 * 第 12 轮之前它是一个 data class，只服务舒尔特一个游戏。加进第二个游戏时发现
 * 「一局的状态」在不同游戏里根本不是一个形状：舒尔特要记「点到第几个了」，找一样要记
 * 「先点中的是哪一张、成没成」。硬塞进一个类里就会长出几个对某个游戏永远无意义的字段，
 * 而那正是「两个字段能互相矛盾」的来源。所以它改成 sealed interface，把公共的五件事
 * （游戏是谁、格子上有什么、给哪句话、完没完、还要点对几下）定下来，各游戏自己实现。
 *
 * 公共部分刻意不含的东西：
 * - 没有「错了几次」「扣了几分」「用了多久」——记错的字段一个都没有（AC-12.3），
 *   用时也不在这一层：它在界面上由 [com.fortheelder.core.time.AppClock] 现算，
 *   算完只活在一屏之内（AC-12.2、DR-09）。
 * - 没有 id、没有时间戳、没有用户字段，所以「这是上次那一局的第几步」这种话
 *   在这里根本无从表达——不落库不是纪律，是没有可以落的地方。
 */
sealed interface PlayRound {

    /** 这是哪一局。 */
    val gameId: String

    /** 格子上按位置摆好的字，长度就是格子数。 */
    val cells: List<String>

    /** 上一下之后该说给用户听的一句话。 */
    val hint: PlayHint

    /** 这一局完了没有。 */
    val finished: Boolean

    /** 这一局要做对几下才算完：舒尔特 25 下，找一样 2 下，记一记 12 下。 */
    val stepsToFinish: Int

    /**
     * 点了第 [index] 格。
     *
     * 实现必须满足两条：越界、或者已经完了的时候原样返回（界面不需要自己防）；
     * 点错的时候除了换一句 [hint]，别的都不动——没有地方可以记下来。
     */
    fun tap(index: Int): PlayRound

    /** 第 [index] 格现在是什么样子。越界返回 [CellMark.PLAIN]。 */
    fun markOf(index: Int): CellMark

    /**
     * 第 [index] 格**现在脸上写着什么**。越界返回空串。
     *
     * 默认实现就是 [cells] 上摆的那个字，所以头三个游戏一个字都不用改。之所以要单开
     * 这么一个口子：记一记的牌背朝上时，「格子上摆着什么」和「该给用户看什么」不是一回事——
     * 把「现在显示什么」也交给这一层管，界面就仍然不认识任何一条游戏规则
     * （`PlayScreen` 里没有一个 `when (round)`）。
     */
    fun faceOf(index: Int): String = cells.getOrNull(index) ?: ""
}

/**
 * 舒尔特方格那种「按顺序挨个点」的一局。
 *
 * [cells] 与 [order] 装的是同一批字，只是顺序不同：[cells] 是摆在格子上的位置，
 * [order] 是该点的先后。这一层不认识「数字」，只认识「相等」和「先后」，
 * 所以换个用字词的同类游戏也不用改这个类。
 */
data class OrderRound(
    override val gameId: String,
    override val cells: List<String>,
    /** 该点的先后顺序。 */
    val order: List<String>,
    /** 已经点对了几个。 */
    val step: Int = 0,
    override val hint: PlayHint = PlayHint.NONE,
) : PlayRound {

    /** 下一个该点的字。已经点完时是空串。 */
    val target: String get() = order.getOrElse(step) { "" }

    /** 该点的先后顺序就是它的长度，不再另存一个能跟它矛盾的字段。 */
    override val stepsToFinish: Int get() = order.size

    override val finished: Boolean get() = step >= order.size

    /**
     * 这一格是不是已经点过了。界面据此把它压暗，用户一眼看得出还剩哪些。
     *
     * 用 `indexOf` 现算而不是另存一张「已点集合」：多一份状态就多一处能对不上的地方。
     */
    fun passed(label: String): Boolean {
        val rank = order.indexOf(label)
        return rank >= 0 && rank < step
    }

    override fun markOf(index: Int): CellMark {
        val label = cells.getOrNull(index) ?: return CellMark.PLAIN
        return if (passed(label)) CellMark.DONE else CellMark.PLAIN
    }

    /**
     * 点对：往前走一步，提示清空。**点错：除了换一句话，什么都不变**——[step] 不动、
     * [cells] 不动，也没有别的地方可以记下来。
     *
     * 返回类型收窄成 [OrderRound]（Kotlin 允许重写时把返回类型变窄），
     * 这样调用它的人拿到的还是这个具体的形状，不用强转。
     */
    override fun tap(index: Int): OrderRound {
        if (finished) return this
        val label = cells.getOrNull(index) ?: return this
        if (label == target) return copy(step = step + 1, hint = PlayHint.NONE)
        val rank = order.indexOf(label)
        return copy(hint = if (rank in 0 until step) PlayHint.ALREADY_DONE else PlayHint.NOT_YET)
    }
}

/**
 * 「找出一对一样的」的一局。
 *
 * 16 张牌全都摆在明面上，其中**恰好两张字一样**；把这两张找出来就算完了。
 * 没有计时、没有翻牌记忆、没有先后顺序——只剩「看」和「找」两个动作。
 *
 * 界面上要压暗什么、要高亮什么，由 [markOf] 说了算：
 * 找到之后**那一对两张都压暗**，第一张选中还没配成的那张是 [CellMark.PICKED]。
 */
data class FindPairRound(
    override val gameId: String,
    override val cells: List<String>,
    /** 要找的那两个字。 */
    val pairLabel: String,
    /** 先点中的那一格；还没点中是 null。 */
    val firstPick: Int? = null,
    /** 那一对找到了没有。 */
    val solved: Boolean = false,
    override val hint: PlayHint = PlayHint.NONE,
) : PlayRound {

    /** 一对就是两张，所以要做对两下。 */
    override val stepsToFinish: Int get() = 2

    override val finished: Boolean get() = solved

    /**
     * 找到之后那一对两张都压暗（用户一眼看得见成了），
     * 先点中的那张亮一下，其余的照旧。
     *
     * 按**位置**算而不是按字算：同样的字在这张盘上出现两次，按字查会把两张一起改掉，
     * 于是第一张刚点下去就看着像成了。
     */
    override fun markOf(index: Int): CellMark {
        val label = cells.getOrNull(index) ?: return CellMark.PLAIN
        if (solved && label == pairLabel) return CellMark.DONE
        if (index == firstPick) return CellMark.PICKED
        return CellMark.PLAIN
    }

    /**
     * 点第 [index] 格。
     *
     * 点的是要找的那个字：第一张记下来，第二张就完事。点的是别的字：
     * 换一句「不是它，再看看」，已经点中的那张不动——用户不需要先把错的取消掉。
     */
    override fun tap(index: Int): FindPairRound {
        if (solved) return this
        val label = cells.getOrNull(index) ?: return this
        if (index == firstPick) return copy(hint = PlayHint.ALREADY_DONE)
        if (label != pairLabel) return copy(hint = PlayHint.NOT_THIS_ONE)
        if (firstPick == null) return copy(firstPick = index, hint = PlayHint.NONE)
        return copy(solved = true, hint = PlayHint.NONE)
    }
}

/**
 * 一个能玩的小游戏。
 *
 * 这是个**数据类**：一个游戏的差异全在它的四个字段和 [deal] 里，没有行为要留空给子类去填，
 * 所以它不是接口（详见 docs/02 第 2.2 节、docs/01 NFR-14）。加一个**规则已经存在的**
 * 游戏（再来一个舒尔特式的、或者再来一个找一样的）确实只往 [PlayCatalog.games] 里多写
 * 一条 `PlayGame(...)`；但加一个**新规则**的游戏要动三处：一个 [PlayRound] 实现、
 * [PlayCatalog] 里一条目录项、界面上的一个分支。第 12 轮加找一样的，走的正是这条路径。
 *
 * 第 41 轮加记一记时，这条路径第一次不够用：它的规则是新的，动词也还是「点某一格」，
 * 但**「格子上现在显示什么」也归领域层管**（牌背朝上时摆着的字不能给用户看）。这一处
 * 就没有去界面里加分支，而是在 [PlayRound] 上加了一个带默认实现的口子 [PlayRound.faceOf]——
 * 头三个游戏一个字没改，界面也只多读了这一句。
 *
 * 这个类里没有等级、没有难度、没有排行榜字段，也没有任何形式的达标线：
 * [longestSeconds] 是**上限**，不是目标，更不是及格线。
 */
data class PlayGame(
    val id: String,
    val name: String,
    /** 一句话说明怎么玩。会被词表扫描，见 [ClaimWords]。 */
    val howTo: String,
    /**
     * 格子一行摆几个。
     *
     * 摆法是游戏自己的事，界面不假定它是 5×5。第二个游戏是 4×4，界面一行都没改。
     */
    val columns: Int,
    /**
     * 这一局大概花多久（秒）。
     *
     * AC-11.3 要的是「一局别拖太久」，3 分钟是给这句话留的余量。这既是界面上那句说明的出处
     * （第 22 轮起界面上说的是「大概 N 分钟」，见 `PlayVoice.promise`），也是测试断言的对象——
     * 不是随便填的文案。它**不做倒计时**：屏幕上跳动的秒数本身就是催促，
     * 而这个游戏的全部意义只是「手上有件事做」。也没有任何东西会在超时后把这一局停下，
     * 所以它是个估时、不是硬上限，界面上也不能被说成硬上限（`records/03` ADR-020）。
     */
    val longestSeconds: Int,
    /** 出一局新的。纯函数：同一个 [Random] 出同一局，所以测试能造出确定的局面。 */
    val deal: (Random) -> PlayRound,
)
