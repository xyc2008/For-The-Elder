package com.fortheelder.domain.play

import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 舒尔特方格的单元测试（JVM，纯值）。
 *
 * 这是 FR-12 四条验收标准里能机械判的那部分：
 * 1 到 25 一个不少、按顺序才推进（AC-12.1）、点错除了换一句话什么都不变（AC-12.3）。
 * AC-12.2 与 AC-12.4 是「没发生什么」，在这里的判法是**结构性的**：
 * [PlayRound] 上没有成绩字段、没有错误次数字段、也没有一局的编号，所以「记下来」这件事
 * 在这条链路上根本无从表达；用例把这一点钉成断言，免得以后有人顺手加一个字段上去。
 */
class SchulteGridTest {

    /** 该点的顺序，也是这局格子该有的全部内容。 */
    private val expectedNumbers: List<String> = (1..25).map { it.toString() }

    private fun board(seed: Int): OrderRound = SchulteGrid.deal(Random(seed))

    /** 按**数字**点。测试关心的是「点错了」，不是「点在第几格」，所以先换成下标。 */
    private fun tapLabel(round: OrderRound, label: String): OrderRound =
        round.tap(round.cells.indexOf(label))

    // ---- 出局：怎么洗、洗掉了谁 ----

    @Test
    fun dealIsAPureFunctionOfTheSeed() {
        assertEquals("同一个种子摆出了两局不一样的格子，测试就钉不住局面了", board(7), board(7))
    }

    @Test
    fun dealShufflesTheCellsInsteadOfReusingTheOrder() {
        val dealt = (1..20).map { board(it).cells }
        assertTrue("开局二十次没有一次把 1 到 25 打乱", dealt.any { it != expectedNumbers })
        assertNotEquals("两个不同的种子摆出了一模一样的格子", dealt[0], dealt[1])
    }

    @Test
    fun dealKeepsEveryNumberExactlyOnce() {
        val round = board(3)
        assertEquals(25, round.cells.size)
        assertEquals("格子里少了或多了数字", expectedNumbers.toSet(), round.cells.toSet())
        assertEquals("该点的顺序必须是 1 到 25 本身，洗的只能是摆法", expectedNumbers, round.order)
    }

    @Test
    fun shufflingNeverChangesTheOrderItself() {
        board(11)
        board(12)
        assertEquals("洗过几次之后该点的顺序跟着变了", expectedNumbers, board(13).order)
    }

    // ---- AC-12.1：按顺序才推进 ----

    @Test
    fun tappingInOrderAdvancesExactlyOneStepEachTime() {
        var round = board(4)
        for (n in 1..25) {
            val label = n.toString()
            assertEquals("第 $n 步该点的是 $label", label, round.target)
            val next = tapLabel(round, label)
            assertEquals("点了 $label 之后步数没往前走一步", round.step + 1, next.step)
            assertEquals("点对了还留着上次的提示", PlayHint.NONE, next.hint)
            round = next
        }
        assertEquals(25, round.step)
        assertTrue("1 到 25 都点过了却还说没结束", round.finished)
        assertEquals("点完了还在报下一个该点谁", "", round.target)
    }

    @Test
    fun aFreshBoardStartsAtTheFirstNumber() {
        for (seed in 0..9) {
            val round = board(seed)
            assertEquals(0, round.step)
            assertFalse(round.finished)
            assertEquals("1", round.target)
            assertEquals(25, round.stepsToFinish)
            assertEquals(PlayHint.NONE, round.hint)
        }
    }

    @Test
    fun passedTracksOnlyTheCellsAlreadyCleared() {
        val round = tapLabel(tapLabel(board(9), "1"), "2")
        assertTrue(round.passed("1"))
        assertTrue(round.passed("2"))
        assertFalse("还没点到的格子被当成点过了", round.passed("3"))
        assertFalse(round.passed("25"))
        assertFalse("不是这一局里的东西不该算点过", round.passed("猫"))
    }

    // ---- AC-12.3：点错不扣分、不计数 ----

    @Test
    fun tappingSomethingAlreadyClearedOnlyChangesTheHint() {
        val round = tapLabel(tapLabel(board(5), "1"), "2")
        val again = tapLabel(round, "1")
        assertEquals("重点一个已经点过的格子，步数却动了", round.step, again.step)
        assertEquals(PlayHint.ALREADY_DONE, again.hint)
        assertEquals("格子的摆法被改动了", round.cells, again.cells)
        assertEquals("该点的顺序被改动了", round.order, again.order)
    }

    @Test
    fun theHintGoesAwayOnTheNextCorrectTap() {
        val wrong = tapLabel(tapLabel(board(5), "1"), "1")
        assertEquals(PlayHint.ALREADY_DONE, wrong.hint)
        assertEquals("点对之后那句提示还留着", PlayHint.NONE, tapLabel(wrong, "2").hint)
    }

    @Test
    fun tappingSomethingStillAheadOnlyChangesTheHint() {
        val round = board(6)
        val ahead = tapLabel(round, "25")
        assertEquals("点了排在后面的数字，步数却动了", 0, ahead.step)
        assertEquals(PlayHint.NOT_YET, ahead.hint)
    }

    @Test
    fun wrongTapsNeverAccumulateAnything() {
        var round = board(8)
        repeat(10) { round = tapLabel(round, "24") }
        assertEquals("点错十次之后步数变了", 0, round.step)
        // 除了那一句提示，这一局和刚开局时一模一样：没有任何地方记下了「错了几次」，
        // 因为 PlayRound 上根本没有能放这个数的字段（AC-12.3）。
        assertEquals(board(8).copy(hint = PlayHint.NOT_YET), round)
    }

    @Test
    fun tappingOutsideTheBoardIsIgnored() {
        val round = board(2)
        assertSame("越界的下标被当成了有效点击", round, round.tap(-1))
        assertSame(round, round.tap(25))
        assertSame(round, round.tap(9_999))
    }

    @Test
    fun tappingAfterTheBoardIsFinishedIsIgnored() {
        var round = board(1)
        for (n in 1..25) round = tapLabel(round, n.toString())
        assertTrue(round.finished)
        assertSame("已经点完了还能继续点", round, round.tap(0))
    }

    // ---- 布局与上限：界面和 AR 都从这里取数 ----

    @Test
    fun theBoardMatchesTheLayoutTheScreenAssumes() {
        val round = board(10)
        assertEquals(SchulteGrid.ID, SchulteGrid.game.id)
        assertEquals("这一局挂的不是自己的 id", SchulteGrid.ID, round.gameId)
        assertEquals(5, SchulteGrid.SIDE)
        assertEquals(SchulteGrid.SIDE, SchulteGrid.game.columns)
        assertEquals("格子数装不满整行，最后一行会缺一格", 0, round.cells.size % SchulteGrid.game.columns)
    }

    @Test
    fun oneRoundStaysInsideThreeMinutes() {
        assertEquals(180, SchulteGrid.game.longestSeconds)
        assertTrue("一局的上限超过了 3 分钟（AC-11.3）", SchulteGrid.game.longestSeconds <= 180)
    }
}
