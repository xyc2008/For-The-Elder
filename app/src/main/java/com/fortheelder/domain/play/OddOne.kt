package com.fortheelder.domain.play

import kotlin.random.Random

/**
 * 找一个不一样的。
 *
 * 屏幕上摆满同一个词，其中两格换成另一个词。点那两格，点完就完了。
 * 点错的格子只回一句「不是它，再看看」——盘面不动，用户接着点别的。
 *
 * 它是这个应用的第六个游戏。它和前五个一样，边界仍是那几条，而且同样是结构性的：
 *
 * - **不报用时**。[OddOneRound] 里没有一个放时间的字段。
 * - **点错没有代价**。只换一句 [PlayHint]（[OddOneRound.found] 不动），也没有一个字段
 *   能记下错了几次（AC-12.3）。
 * - **中途退出不留痕**。全部状态就是一个 [OddOneRound] 值，这一页关掉它就没了（AC-12.4）。
 * - **点对了也不给评级**。做完就是做完，没有分数、没有等级（AC-11.1）。
 *
 * 加它的时候 [PlayRound] 的公共契约一个字没改：动词仍旧是「点某一格」，格子画什么问
 * [PlayRound.markOf]、写什么问 [PlayRound.faceOf]（用默认实现），所以界面里一行都没动。
 * 代价与算一算、找同类那两次一样——界面里几个按游戏分的 `when` 会当场编译不过，把该补的分支
 * 报出来（`PlayCatalog` 类注释里说的第 ③ 种代价，这是它第三次兑现）。详见 `records/03` ADR-121。
 *
 * 与找一样的判据有一处不同：找一样要**恰好两张字一样**，所以要按位置算，不能按字查；
 * 这里要的是**哪两格的字跟大多数不一样**（[CELLS] 格里有 [ODD_COUNT] 格不同），
 * 判据用**构造式**保留来源（[deal] 里先定下哪几格是「不一样」的，再按这个集合摆字），
 * 不靠「按字数一数谁少」——万一两个词碰巧一样，数出来的会有四格，与 [ODD_COUNT] 对不上。
 */
object OddOne {

    const val ID = "odd-one"

    /** 一局摆几个格子。 */
    const val CELLS = 12

    /** 一局里「跟别人不一样」的格子有几个。 */
    const val ODD_COUNT = 2

    /** 一行摆几个格子。四列摆下来不会挤成两行。 */
    const val COLUMNS = 4

    val game: PlayGame = PlayGame(
        id = ID,
        name = "找一个不一样的",
        howTo = "格子里大部分是一样的东西，只有两个跟别人不一样；" +
            "把这两个都点出来就完了。",
        columns = COLUMNS,
        longestSeconds = 60,
        deal = ::deal,
    )

    /**
     * 摆一局：先定下哪 [ODD_COUNT] 格是「不一样」的，再给「一样」的那一批和「不一样」的那一批
     * 各取一个字摆上去。
     *
     * 那两个字从 [CardWords.all] 里取——这张表本来就是按「两个字、日常物件、
     * 两两之间没有一个是另一个的一部分」挑的，拿两个不同位置上的词来做「一样 / 不一样」正合适。
     * 两格的**下标是构造出来的**（先洗出下标集合，再按集合写盘面），不是摆好之后按字数出来的
     * ——理由见类 KDoc。同一个种子出同一局，所以测试能逐格断言。
     */
    fun deal(random: Random): OddOneRound {
        val words = CardWords.all.shuffled(random)
        val common = words[0]
        val odd = words[1]
        val oddIndexes = (0 until CELLS).shuffled(random).take(ODD_COUNT).toSet()
        return OddOneRound(
            gameId = ID,
            cells = List(CELLS) { index -> if (index in oddIndexes) odd else common },
            oddIndexes = oddIndexes,
        )
    }
}

/**
 * 「找一个不一样的」的一局。
 *
 * 一局的全部内容就是：格子上摆着什么 [cells]、哪几格跟别人不一样 [oddIndexes]，
 * 加上已经点出来几格 [found]。
 *
 * [oddIndexes] 存的是**下标**而不是字：盘面上那两格的字是当场从词表里取的，用下标更稳，
 * 也顺带让「已经点出来几格」能直接对着它算（[found] 是下标的集合）。
 *
 * [markOf] 刻意**不用** [CellMark.PICKED]：这个游戏没有「选中了还没成事」这个中间态——
 * 点中一格当场就了了，点错的格子状态一格不变。给点对过的格子留个高亮更糟，用户会以为
 * 那一格还得再点它。
 */
data class OddOneRound(
    override val gameId: String,
    override val cells: List<String>,
    /** 哪几格跟别人不一样（下标）。 */
    val oddIndexes: Set<Int>,
    /** 已经点出来几格。 */
    val found: Set<Int> = emptySet(),
    override val hint: PlayHint = PlayHint.NONE,
) : PlayRound {

    /** 有几格跟别人不一样就要做对几下。 */
    override val stepsToFinish: Int get() = oddIndexes.size

    override val finished: Boolean get() = found.size >= oddIndexes.size

    /** 点出来的格子压暗，其余照旧。理由见类 KDoc。 */
    override fun markOf(index: Int): CellMark =
        if (index in found) CellMark.DONE else CellMark.PLAIN

    /**
     * 点第 [index] 格。
     *
     * 点的是那几格不一样的：记下来，这一格就了了。点的是别的：只换一句「不是它，再看看」——
     * [found] 不动、盘面不动，也没有别的地方可以记下来。
     */
    override fun tap(index: Int): OddOneRound {
        if (finished) return this
        if (index !in cells.indices) return this
        if (index in found) return copy(hint = PlayHint.ALREADY_DONE)
        if (index in oddIndexes) return copy(found = found + index, hint = PlayHint.NONE)
        return copy(hint = PlayHint.NOT_THIS_ONE)
    }
}
