package com.fortheelder.domain.play

import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 单双数的单元测试（JVM，纯值）。
 *
 * 这是 FR-21 四条验收标准里能机械判的那部分：1 到 25 一个不少、**顺序是先单后双**、从 1 起手、
 * 单数点完接双数才推进（AC-21.1）、点错除了换一句话什么都不变（AC-21.2）。
 * AC-21.3 与 AC-21.4 是「没发生什么」，在这里的判法与舒尔特、倒着点那边同一套：**结构性的**——
 * [PlayRound] 上没有成绩字段、没有错误次数字段、也没有一局的编号，所以「记下来」这件事在这条
 * 链路上根本无从表达；用例把这一点钉成断言，免得以后有人顺手加一个字段上去。
 *
 * ## 为什么这一局必须有自己的测试文件（第 158 轮）
 *
 * [PlayCatalogTest] 那批「目录里每个游戏都扫一遍」的断言只钉**形状**：步数不少于两步、格子不是
 * 空的、开局时还没结束、`markOf` 全是 [PlayMark.PLAIN]、目标的字确实在格子上。它们**不认语义**
 * ——[OddEvenGrid] 跟 [SchulteGrid] 共用同一张盘、同一个 1 到 25，把
 * [OddEvenGrid.deal] 里换上的 [OddEvenGrid.order] 改回顺数，盘面还是 25 个格子、目标字还在格子上，
 * 那批断言一条都不会红，而「单双数」已经退化成舒尔特方格了。第 148 轮在「倒着点」上正是这么
 * 试出来的：去掉 `reversed()` 后全仓只有那个文件的三条用例报红。
 *
 * 这一局跟倒着点的差别只有一处、但正是它要考的那一处：**点完单数要换一轮**。所以本文件比
 * [ReverseGridTest] 多钉两条——单数在前双数在后、以及点完最后一个单数之后该点的是 2，不是 25
 * 之后的某一个双数排在前面的写法。
 *
 * 前七个游戏各有一个自己的文件：[SchulteGridTest]、[FindPairTest]、[SubtractSevenTest]、
 * [MemoryTest]、[SameKindTest]、[OddOneTest]、[ReverseGridTest]。这一局是第八个，补上它才配齐。
 */
class OddEvenGridTest {

    /** 这局格子该有的全部内容：1 到 25，一个不少。 */
    private val expectedNumbers: List<String> = (1..25).map { it.toString() }

    /** 该点的顺序：**先十三个单数从小到大，再十二个双数从小到大**。 */
    private val expectedOrder: List<String> =
        expectedNumbers.filter { it.toInt() % 2 == 1 } + expectedNumbers.filter { it.toInt() % 2 == 0 }

    private fun board(seed: Int): OrderRound = OddEvenGrid.deal(Random(seed))

    /** 按**数字**点。测试关心的是「点错了」，不是「点在第几格」，所以先换成下标。 */
    private fun tapLabel(round: OrderRound, label: String): OrderRound =
        round.tap(round.cells.indexOf(label))

    // ---- 出局：怎么洗、洗掉了谁、顺序是哪一拨在前 ----

    @Test
    fun dealIsAPureFunctionOfTheSeed() {
        assertEquals("同一个种子摆出了两局不一样的盘，测试就钉不住局面了", board(7), board(7))
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
        assertEquals("该点的顺序必须是先单后双，洗的只能是摆法", expectedOrder, round.order)
    }

    @Test
    fun theOrderStartsAtOneAndEndsAtTwentyFour() {
        val round = board(6)
        assertNotEquals("该点的顺序还是 1 到 25 顺数，这局就不是单双数了", expectedNumbers, round.order)
        assertEquals("这一局该点的第一个不是 1", "1", round.order.first())
        assertEquals("这一局该点的最后一个不是 24", "24", round.order.last())
        assertEquals("该点的先后不是 25 项", 25, round.order.size)
    }

    @Test
    fun everyOddNumberComesBeforeEveryEvenNumber() {
        val odds = expectedOrder.take(13)
        val evens = expectedOrder.drop(13)
        assertEquals("前十三项里有双数", odds, expectedNumbers.filter { it.toInt() % 2 == 1 })
        assertEquals("后十二项里有单数", evens, expectedNumbers.filter { it.toInt() % 2 == 0 })
        assertEquals("单数的个数不是十三个", 13, odds.size)
        assertEquals("双数的个数不是十二个", 12, evens.size)
    }

    @Test
    fun shufflingNeverChangesTheOrderItself() {
        board(11)
        board(12)
        assertEquals("洗过几次之后该点的顺序跟着变了", expectedOrder, board(13).order)
    }

    // ---- AC-21.1：从 1 起手，先点完单数、再点双数才推进 ----

    @Test
    fun tappingInOrderAdvancesExactlyOneStepEachTime() {
        var round = board(4)
        var n = 0
        for (label in expectedOrder) {
            n += 1
            assertEquals("第 $n 步该点的是 $label", label, round.target)
            val next = tapLabel(round, label)
            assertEquals("点了 $label 之后步数没往前走一步", round.step + 1, next.step)
            assertEquals("点对了还留着上次的提示", PlayHint.NONE, next.hint)
            round = next
        }
        assertEquals(25, round.step)
        assertTrue("单数双数都点过了却还说没结束", round.finished)
        assertEquals("点完了还在报下一个该点谁", "", round.target)
    }

    @Test
    fun theLastOddNumberIsFollowedByTheTwo() {
        var round = board(5)
        for (label in expectedOrder.take(13)) round = tapLabel(round, label)
        assertEquals("十三个单数点完了，下一个该点的不是 2", "2", round.target)
        assertTrue("点完单数就已经算结束了", round.finished.not())
    }

    @Test
    fun aFreshBoardStartsAtTheOne() {
        for (seed in 0..9) {
            val round = board(seed)
            assertEquals(0, round.step)
            assertFalse(round.finished)
            assertEquals("开局该点的不是 1", "1", round.target)
            assertEquals(25, round.stepsToFinish)
            assertEquals(PlayHint.NONE, round.hint)
        }
    }

    @Test
    fun passedTracksOnlyTheCellsAlreadyCleared() {
        val round = tapLabel(tapLabel(board(9), "1"), "3")
        assertTrue(round.passed("1"))
        assertTrue(round.passed("3"))
        assertFalse("还没点到的格子被当成点过了", round.passed("5"))
        assertFalse("双数排在后面，怎么就先算点过了", round.passed("2"))
        assertFalse("不是这一局里的东西不该算点过", round.passed("猫"))
    }

    // ---- AC-21.2：点错不扣分、不计数 ----

    @Test
    fun tappingSomethingAlreadyClearedOnlyChangesTheHint() {
        val round = tapLabel(board(5), "1")
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
        assertEquals("点对之后那句提示还留着", PlayHint.NONE, tapLabel(wrong, "3").hint)
    }

    @Test
    fun tappingSomethingStillAheadOnlyChangesTheHint() {
        val round = board(6)
        val ahead = tapLabel(round, "2")
        assertEquals("点了排在后面的数字，步数却动了", 0, ahead.step)
        assertEquals(PlayHint.NOT_YET, ahead.hint)
    }

    @Test
    fun wrongTapsNeverAccumulateAnything() {
        var round = board(8)
        repeat(10) { round = tapLabel(round, "2") }
        assertEquals("点错十次之后步数变了", 0, round.step)
        // 除了那一句提示，这一局和刚开局时一模一样：没有任何地方记下了「错了几次」，
        // 因为 PlayRound 上根本没有能放这个数的字段（AC-21.2）。
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
        for (label in expectedOrder) round = tapLabel(round, label)
        assertTrue(round.finished)
        assertSame("已经点完了还能继续点", round, round.tap(0))
    }

    // ---- 布局与上限：界面和 AR 都从这里取数 ----

    @Test
    fun theBoardMatchesTheLayoutTheScreenAssumes() {
        val round = board(10)
        assertEquals(OddEvenGrid.ID, OddEvenGrid.game.id)
        assertEquals("这一局挂的不是自己的 id", OddEvenGrid.ID, round.gameId)
        assertEquals(5, OddEvenGrid.SIDE)
        assertEquals(OddEvenGrid.SIDE, OddEvenGrid.game.columns)
        assertEquals("格子数装不满整行，最后一行会缺一格", 0, round.cells.size % OddEvenGrid.game.columns)
    }

    @Test
    fun oneRoundStaysInsideThreeMinutes() {
        assertEquals(180, OddEvenGrid.game.longestSeconds)
        assertTrue("一局的上限超过了 3 分钟（AC-21.3）", OddEvenGrid.game.longestSeconds <= 180)
    }

    // ---- 这一局自己说出来的是什么 ----

    @Test
    fun theGameSaysWhatItIs() {
        assertEquals("单双数", OddEvenGrid.game.name)
        assertTrue("这一局的说明短到没法照着做", OddEvenGrid.game.howTo.length >= 10)
        assertTrue("说明里没写先点什么", OddEvenGrid.game.howTo.contains("单数"))
        assertTrue("说明里没写后点什么", OddEvenGrid.game.howTo.contains("双数"))
        assertTrue("说明里没写点错了会怎样", OddEvenGrid.game.howTo.contains("点错"))
    }
}
