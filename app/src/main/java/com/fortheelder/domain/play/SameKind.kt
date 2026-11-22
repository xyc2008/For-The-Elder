package com.fortheelder.domain.play

import kotlin.random.Random

/**
 * 分好类的一组词，比如「水果」下的香蕉、葡萄、桃子、西瓜。
 *
 * 摆在这里而不是 [KindWords] 里，是因为它是**一局用的形状**：[SameKind.deal] 从
 * [KindWords.kinds] 里挑几组出来，挑出来的就是这几个 [Kind]。
 */
data class Kind(
    /** 这一类叫什么，比如「水果」。会念给用户听，也会被词表扫描。 */
    val label: String,
    /** 这一类下面的词，每个都能摆到格子上。 */
    val words: List<String>,
)

/**
 * 找同类。
 *
 * 屏幕上摆三样东西各几个，混在一起；上面说要找哪一类，用户就把那一类都点出来。
 * 点错只说一句「不是它，再看看」——盘面不动，用户接着点别的。
 *
 * 它是这个应用的第五个游戏。它和前四个一样，边界仍是那四条，而且同样是结构性的：
 *
 * - **不报用时**。[SameKindRound] 里没有一个放时间的字段。
 * - **点错没有代价**。只换一句 [PlayHint]（[SameKindRound.found] 不动），也没有一个字段
 *   能记下错了几次（AC-12.3）。
 * - **中途退出不留痕**。全部状态就是一个 [SameKindRound] 值，这一页关掉它就没了（AC-12.4）。
 * - **点对了也不给评级**。做完就是做完，没有分数、没有等级（AC-11.1）。
 *
 * 加它的时候 [PlayRound] 的公共契约一个字没改：动词仍旧是「点某一格」，格子画什么问
 * [PlayRound.markOf]、写什么问 [PlayRound.faceOf]（用默认实现），所以界面里一行都没动。
 * 代价与算一算那一次一样——界面里几个按游戏分的 `when` 会当场编译不过，把该补的分支报出来
 * （`PlayCatalog` 类注释里说的第 ③ 种代价，这是它第二次兑现）。详见 `records/03` ADR-043。
 *
 * 与找一样的判据有一处不同：找一样要**恰好两张字一样**，所以要按位置算，不能按字查；
 * 这里要找的是**某一类**，盘面上那一类有几张就是几张（[KINDS_ON_BOARD] 组、每组
 * [PER_KIND] 个）。判据用**构造式**保留来源（[deal] 里让每格记住自己来自哪一组），
 * 不靠「按字反查类别」——万一两类的词重了，反查会把不相干的格子也一起算进去。
 */
object SameKind {

    const val ID = "same-kind"

    /** 一局里目标类占用几张格子。 */
    const val PER_KIND = 4

    /** 盘面上混几类东西。 */
    const val KINDS_ON_BOARD = 3

    /** 一行摆几个格子。四列摆下来不会挤成两行。 */
    const val COLUMNS = 4

    val game: PlayGame = PlayGame(
        id = ID,
        name = "找同类",
        howTo = "格子里混着几样东西。上面说要找哪一类，就把那一类都点出来；" +
            "点错了也不要紧，再看看别的。",
        columns = COLUMNS,
        longestSeconds = 120,
        deal = ::deal,
    )

    /**
     * 摆一局：挑 [KINDS_ON_BOARD] 类，定其中一类当目标，全部词摆到格子上。
     *
     * 目标格子的下标是**构造出来的**（`board` 里每一格都带着自己的来源类），不是摆好之后再
     * 按字查类别——理由见类 KDoc。同一个种子出同一局，所以测试能逐格断言。
     */
    fun deal(random: Random): SameKindRound {
        val picked = KindWords.kinds.shuffled(random).take(KINDS_ON_BOARD)
        val target = picked[random.nextInt(picked.size)]
        val board = picked.flatMap { kind -> kind.words.take(PER_KIND).map { kind to it } }
            .shuffled(random)
        return SameKindRound(
            gameId = ID,
            cells = board.map { it.second },
            kindLabel = target.label,
            targets = board.withIndex().filter { it.value.first === target }.map { it.index }.toSet(),
        )
    }
}

/**
 * 「找同类」的一局。
 *
 * 一局的全部内容就是：格子上摆着什么 [cells]、要找哪一类 [kindLabel]、那一类占哪几格
 * [targets]，加上已经点出来几格 [found]。
 *
 * [targets] 存的是**下标**而不是字：盘面上同一个词只会出现一次（每类四个词、盘上三类
 * 各不相同），但用下标更稳，也顺带让「已经点出来几格」能直接对着它算（[found] 是下标的集合）。
 *
 * [markOf] 刻意**不用** [CellMark.PICKED]：这个游戏没有「选中了还没成事」这个中间态——
 * 点中一格当场就了了，点错的格子状态一格不变。给点对过的格子留个高亮更糟，用户会以为
 * 那一格还得再点它。
 */
data class SameKindRound(
    override val gameId: String,
    override val cells: List<String>,
    /** 要找的那一类叫什么。 */
    val kindLabel: String,
    /** 那一类占用哪几格（下标）。 */
    val targets: Set<Int>,
    /** 已经点出来几格。 */
    val found: Set<Int> = emptySet(),
    override val hint: PlayHint = PlayHint.NONE,
) : PlayRound {

    /** 那一类有几格就要做对几下。 */
    override val stepsToFinish: Int get() = targets.size

    override val finished: Boolean get() = found.size >= targets.size

    /** 点出来的格子压暗，其余照旧。理由见类 KDoc。 */
    override fun markOf(index: Int): CellMark =
        if (index in found) CellMark.DONE else CellMark.PLAIN

    /**
     * 点第 [index] 格。
     *
     * 点的是要找那一类的：记下来，这一格就了了。点的是别的：只换一句「不是它，再看看」——
     * [found] 不动、盘面不动，也没有别的地方可以记下来。
     */
    override fun tap(index: Int): SameKindRound {
        if (finished) return this
        if (index !in cells.indices) return this
        if (index in found) return copy(hint = PlayHint.ALREADY_DONE)
        if (index in targets) return copy(found = found + index, hint = PlayHint.NONE)
        return copy(hint = PlayHint.NOT_THIS_ONE)
    }
}
