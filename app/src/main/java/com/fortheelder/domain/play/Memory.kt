package com.fortheelder.domain.play

import kotlin.random.Random

/**
 * 记一记这一局里「摊在明面上的是哪些牌」。
 *
 * 做成一组互斥的取值，而不是「先翻的是哪张」加「有没有一对等着盖回去」两个可空字段：
 * 那两个字段**能同时成立**——先翻的那张还没盖回去、又有一对等着盖回去——可这种局面
 * 在实体牌上根本不存在。字段允许它存在，每个读它们的地方就都得自己防一手，防漏一处就出鬼。
 *
 * 写成 sealed，三种局面就互斥且穷举得完：`when` 少写一支编译不过（第 41 轮 [MemoryRound.tap]
 * 里正是这么用的）。这是本项目一贯的立场——不许出现两个能互相矛盾的字段。
 */
sealed interface MemoryOpening {

    /** 牌全盖着，等着翻第一张。 */
    data object Closed : MemoryOpening

    /** 翻开了一张，等着第二张。 */
    data class Single(val index: Int) : MemoryOpening

    /**
     * 翻开的两张不是一对，等着用户点一下盖回去。
     *
     * 「等着盖回去」是一个**独立局面**而不是「两张翻开」那么简单：在这一局面里点任何一格
     * （除了已经配成对的）都只做「盖回去」这一件事，翻牌得等下一次点。为什么不让它自己
     * 到点盖回去，见 `records/03` ADR-038。
     */
    data class Missed(val first: Int, val second: Int) : MemoryOpening
}

/**
 * 记一记的一局：12 张牌全盖着，一张一张翻，一样的配成一对收起来，六对配完就算完。
 *
 * 牌面取自 [CardWords]，一局里**每个字恰好出现两次**（[deal] 摆的）。
 *
 * 边界跟另外三个游戏一致，而且同样是结构性的：
 *
 * - **没有计时**。这一局不看表：[MemoryOpening.Missed] 是等用户点一下，不是等秒数过去。
 *   领域层的这一包代码里一个时间来源都没有（`PlayStaysClocklessTest` 会扫）。
 * - **没有翻牌次数、没有错误次数、没有分数**。这个类里也没有能放它们的字段：
 *   [solved] 记的是「哪些格子已经收起来了」，不是「点了几下」——点错只换一句 [hint]，
 *   除此之外什么都没动（AC-12.3 是结构上做不到，不是靠自觉）。
 * - **中途退出不留痕**。全部状态就是这个值，没有地方能存下「上次翻到哪了」。
 *
 * 牌背写上 [Memory.BACK] 而不是个符号，理由见 [Memory.BACK]。
 */
data class MemoryRound(
    override val gameId: String,
    override val cells: List<String>,
    /** 已经配成对、收起来了的那些格子的位置。 */
    val solved: Set<Int> = emptySet(),
    /** 明面上现在是什么局面：全盖着、翻开一张、还是一对错的等着盖回去。 */
    val opening: MemoryOpening = MemoryOpening.Closed,
    override val hint: PlayHint = PlayHint.NONE,
) : PlayRound {

    /**
     * 一共几对。
     *
     * 牌面是成对摆的，所以就是牌数的一半。写成计算属性而不是另存一个字段：多一个字段
     * 就多一处能跟 [cells] 对不上的地方（照 [OrderRound.stepsToFinish] 那条老规矩）。
     */
    val pairs: Int get() = cells.size / 2

    /** 六对配完要做对十二下。 */
    override val stepsToFinish: Int get() = pairs * 2

    /** 所有格子都收起来了才算完。 */
    override val finished: Boolean get() = solved.size >= cells.size

    /** 这一格现在是不是摊在明面上。收起来的不算——它们由 [markOf] 和 [faceOf] 单独认。 */
    private fun isFaceUp(index: Int): Boolean = when (val open = opening) {
        MemoryOpening.Closed -> false
        is MemoryOpening.Single -> open.index == index
        is MemoryOpening.Missed -> open.first == index || open.second == index
    }

    /**
     * 这一格现在脸上写着什么。
     *
     * 收起来的牌和正摊着的牌都露出 [cells] 上那个字；**其余一律是 [Memory.BACK]**——
     * 盖着的牌，字不能给用户看，可它仍然摆在 [cells] 里。这就是第 41 轮要在 [PlayRound]
     * 上单开一个口子的原因：界面拿 [cells] 直接画是不对的，可界面又不该懂「记一记的牌
     * 什么时候能看」这条规矩。
     */
    override fun faceOf(index: Int): String {
        if (index !in cells.indices) return ""
        return if (index in solved || isFaceUp(index)) cells[index] else Memory.BACK
    }

    /**
     * 收起来的压暗（用户一眼看得见哪几对已经成了），正摊着的亮一下，盖着的照旧。
     *
     * 按**位置**算而不是按字算：同一个字在这张盘上出现两次，按字查会把两张一起改掉，
     * 于是刚翻开第一张就看着像成对了。这一点跟 [FindPairRound.markOf] 是同一条道理。
     */
    override fun markOf(index: Int): CellMark {
        if (index !in cells.indices) return CellMark.PLAIN
        if (index in solved) return CellMark.DONE
        return if (isFaceUp(index)) CellMark.PICKED else CellMark.PLAIN
    }

    /**
     * 点第 [index] 格。
     *
     * 四种走法，只按 [opening] 分：
     *
     * - 全盖着：翻开这一张，等第二张。
     * - 有一对错的等着盖回去：**任何一格都只做「盖回去」这一件事**，翻开得等下一次点。
     *   不这么做的话，「先盖回去再翻」和「直接翻」会变成两条岔路，用户还不知道那一对怎么就没了。
     * - 翻着一张、点的是同一张：换一句「这个已经点过了」，牌不动。
     * - 翻着一张、点的是另一张：字一样就两张一起收起来，字不一样就记成 [MemoryOpening.Missed]
     *   并说一句 [PlayHint.MISSED_PAIR]。
     *
     * 收起来的格子无论什么时候点，都只说 [PlayHint.ALREADY_DONE]。
     * 越界、或者这一局已经完了，原样返回（界面不需要自己防）。
     */
    override fun tap(index: Int): MemoryRound {
        if (finished) return this
        if (index !in cells.indices) return this
        if (index in solved) return copy(hint = PlayHint.ALREADY_DONE)
        return when (val open = opening) {
            MemoryOpening.Closed ->
                copy(opening = MemoryOpening.Single(index), hint = PlayHint.NONE)

            is MemoryOpening.Missed ->
                copy(opening = MemoryOpening.Closed, hint = PlayHint.NONE)

            is MemoryOpening.Single ->
                when {
                    open.index == index -> copy(hint = PlayHint.ALREADY_DONE)

                    cells[open.index] == cells[index] -> copy(
                        solved = solved + setOf(open.index, index),
                        opening = MemoryOpening.Closed,
                        hint = PlayHint.NONE,
                    )

                    else -> copy(
                        opening = MemoryOpening.Missed(first = open.index, second = index),
                        hint = PlayHint.MISSED_PAIR,
                    )
                }
        }
    }
}

/**
 * 记一记。12 张牌全盖着，一张一张翻，一样的配成一对收起来，六对配完就算完。
 *
 * 它是这个应用的第四个游戏，也是**第一个「格子上写什么」不能直接由 `cells` 说话的游戏**：
 * 盖着的牌不能把字给用户看。为此第 41 轮在 [PlayRound] 上加了一个带默认实现的口子
 * [PlayRound.faceOf]，头三个游戏一个字没改，界面也没有多一个 `when (round)`。
 *
 * 这个游戏当初是被判「顺延」的（[FindPair] 的注释里留着那段判定）。顺延的理由是
 * 「第一张翻开来停多久再盖回去」得让领域层持有时间，而那一层一行时间代码都不许有。
 * 第 41 轮把这条理由推翻了：**翻回去由用户点一下驱动，不由表驱动**（`records/03` ADR-038）。
 * 于是它一秒都不用跟时间打交道，也就没有理由再顺延。
 *
 * 一局的时长上限给 3 分钟：12 张牌要翻到六对，比舒尔特那 25 下更碎，比找一样那 2 下长得多。
 * 它跟另外三个一样是个**估时**，不是硬上限，屏幕上也没有任何东西在数它（ADR-020）。
 */
object Memory {

    const val ID = "memory-6-pairs"

    /** 几对。12 张牌。 */
    const val PAIRS = 6

    /** 一行摆几个。12 张牌 4 列摆 3 行，比 6 列 2 行更贴近实体的牌阵。 */
    const val COLUMNS = 4

    /**
     * 牌背朝上时格子上写的字。
     *
     * 选「盖着」而不是问号之类的符号，理由是**这句话要念给看不见屏幕的人听**：
     * TalkBack 会把「？」一个个念成「问号」，十二张牌就是十二声「问号」，听的人只知道自己
     * 撞上了一堵符号墙；「盖着」念出来是「盖着，第二行第三列」，正好是这个人要的信息。
     * 这也跟本应用一贯的做法一致——有话直说，不用符号让人猜（`records/03` ADR-038）。
     */
    const val BACK = "盖着"

    val game: PlayGame = PlayGame(
        id = ID,
        name = "记一记",
        howTo = "牌都盖着。点一张翻一张，两张一样就收起来，不一样就点一下盖回去。",
        columns = COLUMNS,
        longestSeconds = 180,
        deal = ::deal,
    )

    /**
     * 摆一局：从 [CardWords.all] 里随机挑 6 个字，每个写两张，再打乱位置。
     *
     * 这样摆出来的盘面**恰好**是六对：挑出来的 6 个字两两不同（词池里没有重复），
     * 每个各写两张，于是每个字出现恰好两次、任意不同的两个字配不成对。
     *
     * 用 `shuffled(random)` 而不是 `shuffled()`：同一个种子出同一局，测试才能造出确定的局面。
     *
     * 摆法是一步到位的：先挑字、每个写两张、再打乱，没有「摆出来不对就重摆」那种循环。
     * 一次成型才有一句能写进测试的话——同一颗种子出同一局，中间没有别的分支。
     */
    fun deal(random: Random): MemoryRound {
        val picks = CardWords.all.shuffled(random).take(PAIRS)
        return MemoryRound(gameId = ID, cells = (picks + picks).shuffled(random))
    }
}
