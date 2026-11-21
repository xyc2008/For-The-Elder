package com.fortheelder.domain.play

import kotlin.random.Random

/**
 * 算一算（一百往下每次减七）。
 *
 * 屏幕上一次只问一道：「93 减 7 是多少？」下面三个数，把对的那个点出来。点对了下一道，
 * 点错了只说一句「不是它，再看看」——题面不动，用户接着点别的。
 *
 * 它是这个应用的第三个游戏。前两个（舒尔特方格、找出一样的）都是「看和点」，
 * 这一个多了一步心算，但仍然只有 [PlayRound.tap] 那一个动词：**答就是点某一格**。
 * 所以它进来的时候 [PlayRound] 的公共契约一个字没改。
 *
 * 三条边界与前两个一致，而且同样是结构性的：
 *
 * - **不报用时**。这一局不看表（[SubtractRound] 里没有任何放时间的字段）。少一次
 *   「算五道用了多久」就少一次心算测评——那种话 AC-11.1 明禁说出口。
 * - **点错没有代价**。只换一句 [PlayHint]，[SubtractRound.step] 不动，也没有一个
 *   字段能记下错了几次（AC-12.3）。
 * - **中途退出不留痕**。这一局的全部状态就是一个 [SubtractRound] 值，这一页关掉它就没了，
 *   库里也不会多出一条（AC-12.4、DR-09）。
 *
 * 名字上有一处要说明：这条玩法的技术名是「连续减七」，但**用户可见的名字只能是「算一算」**。
 * 「连续」两个字在两张已生效的词表里都是禁词（`StarterOptions.payToJoinWords` 与
 * `Encouragement.gameWords`），措辞上也就一并避开了。[ID] 保留 `subtract-seven`
 * 这种描述规则本身的写法，它不随文案改动。
 */
object SubtractSeven {

    const val ID = "subtract-seven"

    /** 从哪个数开始。 */
    const val START = 100

    /** 每次减掉几。 */
    const val DEDUCTION = 7

    /** 一局几道题。 */
    const val QUESTION_COUNT = 5

    /** 一行摆几个格子。每题正好三个数，一行放得下，不用换行去认。 */
    const val COLUMNS = 3

    /**
     * 干扰项相对**正确答案**的偏移，挑两个不同的用。
     *
     * 这五个都在「一眼像是算错了」的范围里：差 1、差 2 是进位退位错了一格，差 10 是错退了一位。
     * **故意不放 `+10`**：比答案大十的数是屏幕上最扎眼的那个，出现两次就会变成一种提示，
     * 用户不用算，挑大的就行。两个偏移必须不同（见 [deal]），于是三个数两两不等。
     */
    private val offsets: List<Int> = listOf(-10, -2, -1, 1, 2)

    val game: PlayGame = PlayGame(
        id = ID,
        name = "算一算",
        howTo = "从 100 开始，每次减掉 7。每题有三个数，把对的那个点出来。点错了也不要紧，再看看别的。",
        columns = COLUMNS,
        longestSeconds = 180,
        deal = ::deal,
    )

    /**
     * 摆一局：这一局要问的**全部 5 道题在这一刻就算好**。
     *
     * 为什么不是答一道算一道：答一道的入口是 [PlayRound.tap]，它**没有 `Random` 可传**
     * （那个签名是公共契约，为一个游戏改它要动所有实现）。题面得在出局时一次算齐，
     * [SubtractRound.tap] 只负责往前走一格。
     *
     * 这么做还顺手换来两件事：同一个种子出同一局（测试能逐题断言），以及**几道题问什么是
     * 一局开始时就定下的**——中途不会因为重算而变。
     */
    fun deal(random: Random): SubtractRound {
        val questions = (0 until QUESTION_COUNT).map { index ->
            val current = START - DEDUCTION * index
            val answer = current - DEDUCTION
            val twoOffsets = offsets.shuffled(random).take(2)
            SubtractQuestion(
                current = current,
                choices = (twoOffsets.map { answer + it } + answer).shuffled(random),
            )
        }
        return SubtractRound(gameId = ID, deduction = DEDUCTION, questions = questions)
    }
}

/**
 * 一道题。
 *
 * [current] 是题目里念出来的那个数，[choices] 是三个选项（其中一个是对的）。
 * 答案**不存一个字段**：它就是 `current - deduction`，存下来就多了一处能跟 [current] 对不上的地方。
 */
data class SubtractQuestion(
    val current: Int,
    val choices: List<Int>,
)

/**
 * 「算一算」的一局。
 *
 * 一局的全部内容就是 [questions] 那几道题（出局时算好、之后不变）加上走到第几道 [step]。
 * 现在这道题是 [question]，答完了换下一道；5 道都答对就 [finished]。
 *
 * [markOf] 在这个游戏里恒为 [CellMark.PLAIN]——它**刻意**不用 [CellMark.PICKED]：
 * 那个取值在找一样里说的是「这张选中了、还没配上」，而这里没有这个中间态（答对立刻换题、
 * 答错状态一格不变）。给答过一次的选项留个高亮更糟，用户会以为那一格还得再点它。
 */
data class SubtractRound(
    override val gameId: String,
    /** 每次减掉几。 */
    val deduction: Int,
    /** 这一局的题，出局时就全部算好（[SubtractSeven.deal] 是唯一的产出者）。 */
    val questions: List<SubtractQuestion>,
    /** 已经答对几道。 */
    val step: Int = 0,
    override val hint: PlayHint = PlayHint.NONE,
) : PlayRound {

    /** 现在这道题。5 道都答完了是 `null`。 */
    val question: SubtractQuestion? get() = questions.getOrNull(step)

    /**
     * 现在念出来的那个数。
     *
     * 答完之后停在最后一道上——那时屏幕上已经换成结果卡，这个值不会被念出来
     * （[com.fortheelder.ui.play.PlayVoice.headline] 只在还没答完时用）。
     */
    val current: Int get() = (question ?: questions.last()).current

    /** 现在这道题的答案；答完了是 `null`。 */
    val answer: Int? get() = question?.let { it.current - deduction }

    /** 现在这道题的三个数。答完了就没有格子可点了。 */
    override val cells: List<String> get() = question?.choices?.map(Int::toString) ?: emptyList()

    /** 5 道题都答对才算完，所以要做对 5 下。 */
    override val stepsToFinish: Int get() = questions.size

    override val finished: Boolean get() = step >= questions.size

    /**
     * 恒返回 [CellMark.PLAIN]。
     *
     * 理由见类 KDoc：这个游戏没有「选中了还没成事」这个中间态，而 `DONE` 也不行——
     * 答对的那一格下一瞬间就换成了别的数，把它压暗是在说一件已经过去的事。
     */
    override fun markOf(index: Int): CellMark = CellMark.PLAIN

    /**
     * 点第 [index] 格。
     *
     * 点的是对的：走到下一道，提示清空。点的是别的：只换一句「不是它，再看看」——
     * [step] 不动、题面不动，也没有别的地方可以记下来。
     */
    override fun tap(index: Int): SubtractRound {
        if (finished) return this
        val picked = question?.choices?.getOrNull(index) ?: return this
        if (picked == answer) return copy(step = step + 1, hint = PlayHint.NONE)
        return copy(hint = PlayHint.NOT_THIS_ONE)
    }
}
