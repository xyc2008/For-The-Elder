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
 * 找出一样的：单元测试（JVM，纯值）。
 *
 * 第二个游戏进来之后，这一层的测试要回答两个不同的问题：
 *
 * 1. **这一局能不能玩**。恰好一对（多一对就永远找不完，一对都没有就永远找不到）、
 *    点错了盘面不动、点完两张才结束。
 * 2. **它没有偷偷跟着舒尔特长出别的东西**。舒尔特那套「顺序、进度、该点谁」在找一样里
 *    一格都没有，而这一层**也没有用时、没有错误次数**——[FindPairRound] 上根本没有能放
 *    这两个数的字段，所以 AC-11.2、AC-12.2 在这里同样是结构性地做不到，不是靠自觉。
 */
class FindPairTest {

    private fun board(seed: Int): FindPairRound = FindPair.deal(Random(seed))

    /** 要找的那两张分别在哪个位置。用位置而不是用字来写断言，界面那一层也是这么画的。 */
    private fun pairIndices(round: FindPairRound): List<Int> =
        round.cells.indices.filter { round.cells[it] == round.pairLabel }

    private fun firstOf(round: FindPairRound, predicate: (String) -> Boolean): Int =
        round.cells.indices.first { predicate(round.cells[it]) }

    // ---- 出局：盘面上是什么 ----

    @Test
    fun dealIsAPureFunctionOfTheSeed() {
        assertEquals("同一个种子摆出了两局不一样的盘面，测试就钉不住局面了", board(7), board(7))
    }

    @Test
    fun everyBoardIsFourByFour() {
        for (seed in 0..49) {
            val round = board(seed)
            assertEquals("16 张牌的盘面数不对", FindPair.SIDE * FindPair.SIDE, round.cells.size)
            assertEquals("格子数装不满整行，最后一行会缺一格", 0, round.cells.size % FindPair.game.columns)
        }
    }

    @Test
    fun everyBoardHasExactlyOnePairAndTheRestAreSingular() {
        for (seed in 0..49) {
            val round = board(seed)
            val counts = round.cells.groupingBy { it }.eachCount()
            val twice = counts.filterValues { it == 2 }
            assertEquals(
                "种子 $seed 下盘面上有 ${twice.size} 对一样的字；多一对就永远找不完",
                1,
                twice.size,
            )
            assertEquals("那一对不是 pairLabel", setOf(round.pairLabel), twice.keys)
            assertEquals(
                "除了那一对还有重复的字，会让人以为找错了",
                round.cells.size - 2,
                counts.values.count { it == 1 },
            )
        }
    }

    @Test
    fun theBoardOnlyUsesTheDeclaredWordPool() {
        for (seed in 0..49) {
            val round = board(seed)
            assertTrue(
                "种子 $seed 下摆出了词池以外的字：${round.cells - FindPair.words.toSet()}",
                FindPair.words.containsAll(round.cells),
            )
            assertTrue("pairLabel 不在词池里", FindPair.words.contains(round.pairLabel))
        }
    }

    @Test
    fun noWordIsPartOfAnother() {
        for (a in FindPair.words) {
            for (b in FindPair.words) {
                if (a === b || a == b) continue
                assertFalse(
                    "「$a」是「$b」的一部分，两张牌看起来会像同一张",
                    b.contains(a),
                )
            }
        }
        assertEquals("词池里有重复的字", FindPair.words.size, FindPair.words.toSet().size)
    }

    @Test
    fun twoSeedsDoNotAlwaysDealTheSameBoard() {
        val dealt = (0..19).map { board(it) }
        assertNotEquals("每次摆的都一模一样", dealt[0].cells, dealt[1].cells)
    }

    // ---- 开局的样子 ----

    @Test
    fun aFreshBoardHasNothingMarkedAndNoHint() {
        for (seed in 0..19) {
            val round = board(seed)
            assertEquals(PlayHint.NONE, round.hint)
            assertNull("还没点就已经有一张被挑起来了", round.firstPick)
            assertFalse(round.finished)
            assertTrue(
                "一进去就有格子被压暗了",
                round.cells.indices.all { round.markOf(it) == CellMark.PLAIN },
            )
        }
    }

    @Test
    fun itTakesTwoCorrectTapsToFinish() {
        assertEquals(2, board(1).stepsToFinish)
        assertEquals(2, FindPair.SIDE * FindPair.SIDE / 2 - 6)
    }

    // ---- 点错的全部后果就是一句话 ----

    @Test
    fun tappingSomethingElseOnlyChangesTheHint() {
        val round = board(3)
        val wrong = firstOf(round) { it != round.pairLabel }
        val after = round.tap(wrong)
        assertEquals(PlayHint.NOT_THIS_ONE, after.hint)
        assertNull("点错之后竟然记住了一张牌", after.firstPick)
        assertFalse("点错竟然算成了找到", after.solved)
        assertEquals("点错之后盘面被改了", round.cells, after.cells)
        assertNotEquals(round, after)
    }

    @Test
    fun wrongTapsNeverAccumulateAnything() {
        val round = board(8)
        val wrong = firstOf(round) { it != round.pairLabel }
        var after: FindPairRound = round
        repeat(10) { after = after.tap(wrong) }
        // 除了那一句提示，这一局和刚开局时一模一样：**连「哪张牌被点过」都没记**——
        // 点错既不推进、也不留下痕迹（firstPick 仍然是 null），更没有地方记下「错了几次」，
        // 因为 FindPairRound 上根本没有能放这个数的字段。
        assertEquals(round.copy(hint = PlayHint.NOT_THIS_ONE), after)
        assertNull(after.firstPick)
    }

    @Test
    fun aWrongTapDoesNotPutDownTheCardAlreadyPicked() {
        val round = board(5)
        val (first, second) = pairIndices(round)
        val wrong = firstOf(round) { it != round.pairLabel }
        val picked = round.tap(first)
        assertEquals(first, picked.firstPick)
        val afterWrong = picked.tap(wrong)
        assertEquals("点了别的字，已经挑起来的那张被放下了", first, afterWrong.firstPick)
        assertEquals(PlayHint.NOT_THIS_ONE, afterWrong.hint)
        assertEquals(CellMark.PICKED, afterWrong.markOf(first))
        // 挑起来的那张还在，所以再点第二张照样能成——用户不需要先把错的那下撤掉。
        assertTrue(afterWrong.tap(second).finished)
    }

    // ---- 找齐两张就结束 ----

    @Test
    fun theFirstCorrectTapOnlyPicksThatOneCardUp() {
        val round = board(9)
        val (first, second) = pairIndices(round)
        val picked = round.tap(first)
        assertFalse("点中第一张就算完了", picked.finished)
        assertEquals(PlayHint.NONE, picked.hint)
        assertEquals(CellMark.PICKED, picked.markOf(first))
        assertEquals(
            "还没找到的第二张也被压暗了，看着像是两张一起成了",
            CellMark.PLAIN,
            picked.markOf(second),
        )
    }

    @Test
    fun tappingTheSameCardTwiceIsOnlyAHint() {
        val round = board(11)
        val first = pairIndices(round).first()
        val again = round.tap(first).tap(first)
        assertEquals(PlayHint.ALREADY_DONE, again.hint)
        assertEquals(first, again.firstPick)
        assertFalse(again.finished)
    }

    @Test
    fun theSecondCorrectTapFinishesTheRound() {
        val round = board(13)
        val (first, second) = pairIndices(round)
        val done = round.tap(first).tap(second)
        assertTrue("两张都点到了却还说没结束", done.finished)
        assertEquals(PlayHint.NONE, done.hint)
        assertTrue("找到之后第一张没有压暗", done.markOf(first) == CellMark.DONE)
        assertTrue("找到之后第二张没有压暗", done.markOf(second) == CellMark.DONE)
        assertEquals(
            "找到之后别的格子被改了",
            round.cells.indices.count { done.markOf(it) != CellMark.PLAIN },
            2,
        )
    }

    @Test
    fun tappingAfterItIsSolvedIsIgnored() {
        val round = board(17)
        val (first, second) = pairIndices(round)
        val done = round.tap(first).tap(second)
        assertTrue(done.finished)
        assertSame("已经找齐了还能继续点", done, done.tap(first))
        assertSame(done, done.tap(second))
        assertSame(done, done.tap(0))
    }

    @Test
    fun tappingOutsideTheBoardIsIgnored() {
        val round = board(2)
        assertSame("越界的下标被当成了有效点击", round, round.tap(-1))
        assertSame(round, round.tap(FindPair.SIDE * FindPair.SIDE))
        assertSame(round, round.tap(9_999))
        assertEquals("越界的位置不该是挑起来的样子", CellMark.PLAIN, round.markOf(99))
    }

    // ---- 布局与上限：界面和 AR 都从这里取数 ----

    @Test
    fun theBoardMatchesTheLayoutTheScreenAssumes() {
        val round = board(10)
        assertEquals(FindPair.ID, FindPair.game.id)
        assertEquals("这一局挂的不是自己的 id", FindPair.ID, round.gameId)
        assertEquals(4, FindPair.SIDE)
        assertEquals(FindPair.SIDE, FindPair.game.columns)
        assertEquals("16 张牌摊在 4×4 里正好", FindPair.SIDE * FindPair.SIDE, round.cells.size)
    }

    @Test
    fun oneRoundStaysInsideTwoMinutes() {
        assertEquals(120, FindPair.game.longestSeconds)
        assertTrue("一局的上限超过了 3 分钟（AC-11.3）", FindPair.game.longestSeconds <= 180)
    }

    @Test
    fun theGameSaysHowToPlayItWithoutBoasting() {
        assertEquals("找出一样的", FindPair.game.name)
        assertTrue(
            "玩法说明只有 ${FindPair.game.howTo.length} 个字，照它玩不起来",
            FindPair.game.howTo.length >= 10,
        )
    }
}
