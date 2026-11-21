package com.fortheelder.domain.play

import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 算一算：单元测试（JVM，纯值）。
 *
 * 第三个游戏进来之后，这一层的测试要回答三个不同的问题：
 *
 * 1. **这一局能不能算**。五道题、每题三个不一样的数、有且只有一个对、点对了往前走、
 *    点错了只换一句话。
 * 2. **它有没有被前两个游戏带出别的东西**。舒尔特的「现在点几」和找一样的「选中了还没配上」
 *    在这里一格都没有（[SubtractRound.markOf] 恒 `PLAIN`）。
 * 3. **它有没有偷偷开始计时或记分**。这道题以前是评审项，这一轮起有机械形式了：
 *    `SubtractRound` 上根本没有像时间的字段（见 [theRoundKeepsNoPlaceToPutATime]），
 *    而整个 `domain/play` 目录不认时钟由 [PlayStaysClocklessTest] 守着。
 *    AC-11.2、AC-12.2、AC-12.3 在这里是结构性地做不到，不是靠自觉。
 */
class SubtractSevenTest {

    private fun round(seed: Int): SubtractRound = SubtractSeven.deal(Random(seed))

    /** 现在这道题的正确项在第几格。界面那层也是这么找的（按值，不是按位置）。 */
    private fun answerIndex(round: SubtractRound): Int =
        round.question!!.choices.indexOf(round.answer!!)

    /** 现在这道题里一个错的选项在第几格。 */
    private fun wrongIndex(round: SubtractRound): Int =
        round.question!!.choices.indexOfFirst { it != round.answer }

    // ---- 出局：这一局是什么 ----

    @Test
    fun dealIsAPureFunctionOfTheSeed() {
        assertEquals("同一个种子摆出了两局不一样的题，测试就钉不住局面了", round(7), round(7))
    }

    @Test
    fun aRoundIsFiveQuestionsCountingDownBySeven() {
        for (seed in 0..19) {
            val dealt = round(seed)
            assertEquals(
                "种子 $seed 下的题数不对",
                SubtractSeven.QUESTION_COUNT,
                dealt.questions.size,
            )
            dealt.questions.forEachIndexed { index, question ->
                assertEquals(
                    "第 ${index + 1} 道题的起点算错了：应该从 ${SubtractSeven.START} 每次减 " +
                        "${SubtractSeven.DEDUCTION}",
                    SubtractSeven.START - SubtractSeven.DEDUCTION * index,
                    question.current,
                )
            }
            assertEquals("第一道不该是 100 起的", 100, dealt.questions.first().current)
            assertEquals("最后一道的起点不对", 72, dealt.questions.last().current)
            assertEquals("最后一道的答案不该超过 100 以下第一个整数", 65, dealt.questions.last().current - 7)
        }
    }

    @Test
    fun everyQuestionOffersThreeDistinctNumbersWithExactlyOneAnswer() {
        for (seed in 0..49) {
            val dealt = round(seed)
            for (question in dealt.questions) {
                val answer = question.current - SubtractSeven.DEDUCTION
                assertEquals(
                    "「${question.current} 减 ${SubtractSeven.DEDUCTION}」给了 ${question.choices.size} 个数，" +
                        "摆不下也点不着",
                    SubtractSeven.COLUMNS,
                    question.choices.size,
                )
                assertEquals(
                    "「${question.current}」这题里有两个一样的数：${question.choices}，划不出对错",
                    question.choices.size,
                    question.choices.toSet().size,
                )
                assertEquals(
                    "「${question.current}」这题里对的不止一个：${question.choices}",
                    1,
                    question.choices.count { it == answer },
                )
                assertTrue(
                    "「${question.current}」这题里摆出了 0 或负数：${question.choices}",
                    question.choices.all { it > 0 },
                )
            }
        }
    }

    @Test
    fun noChoiceIsTheAnswerPlusTen() {
        // 比答案大十的数是屏幕上最扎眼的那个（错退一位看着就像它）。它出现两次就会变成
        // 一种提示：用户不用算，挑大的就行。offsets 里故意没有 +10（见 SubtractSeven）。
        for (seed in 0..49) {
            for (question in round(seed).questions) {
                val answer = question.current - SubtractSeven.DEDUCTION
                assertFalse(
                    "「${question.current}」这题里出现了比答案大十的 ${answer + 10}：${question.choices}",
                    question.choices.contains(answer + 10),
                )
            }
        }
    }

    @Test
    fun twoSeedsDoNotAlwaysDealTheSameChoices() {
        assertNotEquals("每次摆的三个数一模一样", round(0).questions, round(1).questions)
    }

    @Test
    fun theAnswerIsNotAlwaysInTheSamePlace() {
        val places = (0..29).map { answerIndex(round(it)) }.toSet()
        assertTrue(
            "答案总在第 $places 格——用户点一个位置就能一直过，不用算",
            places.size >= 2,
        )
    }

    // ---- 开局的样子 ----

    @Test
    fun aFreshRoundStartsAtTheFirstQuestionWithNothingMarked() {
        for (seed in 0..19) {
            val dealt = round(seed)
            assertEquals(0, dealt.step)
            assertFalse("还没答一道就说结束了", dealt.finished)
            assertEquals(PlayHint.NONE, dealt.hint)
            assertEquals("开局念的数不是第一道的", dealt.questions.first().current, dealt.current)
            assertEquals(
                "开局摆出来的格子不是第一道的三个数",
                dealt.questions.first().choices.map(Int::toString),
                dealt.cells,
            )
            assertTrue(
                "一进去就有格子被标记了",
                dealt.cells.indices.all { dealt.markOf(it) == CellMark.PLAIN },
            )
        }
    }

    @Test
    fun itTakesExactlyFiveCorrectTapsToFinish() {
        val dealt = round(1)
        assertEquals(SubtractSeven.QUESTION_COUNT, dealt.stepsToFinish)
        assertEquals("五道题却要说点五下以上", dealt.questions.size, dealt.stepsToFinish)
    }

    // ---- 点对了往前走一格 ----

    @Test
    fun tappingTheRightNumberMovesToTheNextQuestion() {
        val dealt = round(3)
        val after = dealt.tap(answerIndex(dealt))
        assertEquals("点对了没往前走", 1, after.step)
        assertEquals(PlayHint.NONE, after.hint)
        assertFalse(after.finished)
        assertEquals("换了一道题却还在念上一道的数", dealt.questions[1].current, after.current)
        assertEquals(
            "格子没有换成下一道的三个数",
            dealt.questions[1].choices.map(Int::toString),
            after.cells,
        )
    }

    @Test
    fun fiveCorrectTapsFinishTheRound() {
        var current: SubtractRound = round(5)
        repeat(SubtractSeven.QUESTION_COUNT) {
            assertFalse("第 ${it + 1} 下之前就说结束了", current.finished)
            current = current.tap(answerIndex(current))
        }
        assertTrue("五道都答对了却还说没结束", current.finished)
        assertEquals(SubtractSeven.QUESTION_COUNT, current.step)
        assertEquals("答完之后不该还留着提示", PlayHint.NONE, current.hint)
        assertNull("答完之后还留着一道题", current.question)
        assertNull("答完之后还能取到答案", current.answer)
        assertTrue("答完了屏幕上还有格子可点", current.cells.isEmpty())
    }

    // ---- 点错的全部后果就是一句话 ----

    @Test
    fun tappingAWrongNumberOnlyChangesTheHint() {
        val dealt = round(8)
        val after = dealt.tap(wrongIndex(dealt))
        assertEquals(PlayHint.NOT_THIS_ONE, after.hint)
        assertEquals("点错了竟然往前走了", 0, after.step)
        assertEquals("点错了题面被改了", dealt.questions, after.questions)
        assertEquals("点错了格子被改了", dealt.cells, after.cells)
        assertFalse(after.finished)
        assertNotEquals(dealt, after)
    }

    @Test
    fun wrongTapsNeverAccumulateAnything() {
        val dealt = round(11)
        val wrong = wrongIndex(dealt)
        var after: SubtractRound = dealt
        repeat(10) { after = after.tap(wrong) }
        // 除了那一句提示，这一局和刚开局时一模一样：**连「点过哪一格」都没记**，
        // 更没有地方记下「错了几次」——因为 SubtractRound 上根本没有能放这个数的字段。
        assertEquals(dealt.copy(hint = PlayHint.NOT_THIS_ONE), after)
        assertEquals("错点累积出了推进", 0, after.step)
    }

    @Test
    fun aWrongTapDoesNotUndoTheQuestionsAlreadyAnswered() {
        val dealt = round(13)
        val afterOne = dealt.tap(answerIndex(dealt))
        val afterWrong = afterOne.tap(wrongIndex(afterOne))
        assertEquals("点错把已经答对的那道退回去了", 1, afterWrong.step)
        assertEquals(PlayHint.NOT_THIS_ONE, afterWrong.hint)
        // 结果不变，所以接着点对的照样能成——用户不需要先把错的那下撤掉。
        assertEquals("点错之后接着点对的，没能往前走", 2, afterWrong.tap(answerIndex(afterWrong)).step)
    }

    // ---- 边界：答完之后、格子之外 ----

    @Test
    fun tappingAfterItIsFinishedIsIgnored() {
        var done: SubtractRound = round(17)
        repeat(SubtractSeven.QUESTION_COUNT) { done = done.tap(answerIndex(done)) }
        assertTrue(done.finished)
        assertSame("已经答完了还能继续点", done, done.tap(0))
        assertSame(done, done.tap(-1))
        assertSame(done, done.tap(9_999))
    }

    @Test
    fun tappingOutsideTheThreeNumbersIsIgnored() {
        val dealt = round(2)
        assertSame("越界的下标被当成了有效点击", dealt, dealt.tap(-1))
        assertSame(dealt, dealt.tap(SubtractSeven.COLUMNS))
        assertSame(dealt, dealt.tap(9_999))
        assertEquals("越界的位置不该有任何标记", CellMark.PLAIN, dealt.markOf(99))
    }

    @Test
    fun markOfIsAlwaysPlainInEveryState() {
        val dealt = round(4)
        fun assertAllPlain(round: SubtractRound, where: String) {
            assertTrue(
                "$where 有格子不是 PLAIN 的样子",
                round.cells.indices.all { round.markOf(it) == CellMark.PLAIN },
            )
        }
        assertAllPlain(dealt, "开局时")
        assertAllPlain(dealt.tap(wrongIndex(dealt)), "点错之后")
        val next = dealt.tap(answerIndex(dealt))
        assertAllPlain(next, "答对一道之后")
        // 这个游戏刻意不用 PICKED 也不用 DONE：没有「选中了还没成事」这个中间态，
        // 答对的那一格下一瞬间就换成了别的数（理由见 SubtractSeven 里的类 KDoc）。
        assertEquals(CellMark.PLAIN, dealt.markOf(0))
        assertEquals(CellMark.PLAIN, next.markOf(0))
    }

    // ---- 它没有偷偷长出别的东西 ----

    @Test
    fun theRoundKeepsNoPlaceToPutATime() {
        val names = SubtractRound::class.java.declaredFields.map { it.name }
        assertTrue(
            "SubtractRound 的字段清单变了（现在是 $names），这条「没有地方放用时」的断言得重写",
            names.containsAll(listOf("gameId", "deduction", "questions", "step", "hint")),
        )
        val timeLike = listOf("time", "second", "milli", "duration", "elapsed", "clock", "started")
        for (name in names) {
            val lower = name.lowercase()
            for (word in timeLike) {
                assertFalse(
                    "SubtractRound 上有一个像时间的字段 `$name`——计时就是从这种字段开始的（AC-11.2）",
                    lower.contains(word),
                )
            }
        }
    }

    // ---- 布局与上限：界面和 AR 都从这里取数 ----

    @Test
    fun theRoundMatchesTheLayoutTheScreenAssumes() {
        val dealt = round(10)
        assertEquals(SubtractSeven.ID, SubtractSeven.game.id)
        assertEquals("这一局挂的不是自己的 id", SubtractSeven.ID, dealt.gameId)
        assertEquals("减数只在题目算的时候用一次，摆上桌的规则只在这里", SubtractSeven.DEDUCTION, dealt.deduction)
        assertEquals(
            "格子数装不满整行，最后一行会缺一格",
            0,
            dealt.cells.size % SubtractSeven.game.columns,
        )
        assertEquals(SubtractSeven.COLUMNS, SubtractSeven.game.columns)
    }

    @Test
    fun oneRoundStaysInsideThreeMinutes() {
        assertEquals(180, SubtractSeven.game.longestSeconds)
        assertTrue("一局的上限超过了 3 分钟（AC-11.3）", SubtractSeven.game.longestSeconds <= 180)
    }

    // ---- 名字与说明 ----

    @Test
    fun theVisibleNameAvoidsTheBannedWord() {
        // 这条玩法的技术名是「连续减七」，但两个字的可见名不能带「连续」：
        // 「连续」在文案纪律表 CopyRules.streakWords 里是禁词（第 37 轮之前这条规矩有两张表，
        // 其中 StarterOptions.payToJoinWords 那张里并没有「连续」）。名字换个说法，规则一个字没改。
        assertFalse("可见的游戏名里带了禁词「连续」", SubtractSeven.game.name.contains("连续"))
        assertFalse("玩法说明里带了禁词「连续」", SubtractSeven.game.howTo.contains("连续"))
    }

    @Test
    fun theGameSaysHowToPlayItWithoutBoasting() {
        assertEquals("算一算", SubtractSeven.game.name)
        assertTrue(
            "玩法说明只有 ${SubtractSeven.game.howTo.length} 个字，照它玩不起来",
            SubtractSeven.game.howTo.length >= 10,
        )
        assertTrue("玩法说明里没有说要减几", SubtractSeven.game.howTo.contains("7"))
    }
}
