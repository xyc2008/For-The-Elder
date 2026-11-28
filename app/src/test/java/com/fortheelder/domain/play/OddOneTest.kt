package com.fortheelder.domain.play

import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 找一个不一样的：单元测试（JVM，纯值）。
 *
 * 第六个游戏进来之后，这一层的测试要回答四个问题：
 *
 * 1. **这一局能不能玩**。盘上摆的是两个字，其中恰好两格跟别人不一样；点一个少一个，
 *    两格都点出来才算完。
 * 2. **它有没有被前五个游戏带出别的东西**。找一样的「选中了还没配上」、找同类的
 *    「要找的那一类」在这里都不是一回事：[OddOneRound.markOf] 只会是 `DONE` 或 `PLAIN`，
 *    从来没有 `PICKED`。
 * 3. **它有没有偷偷开始计时或记分**。`OddOneRound` 上根本没有像时间的字段
 *    （见 [theRoundKeepsNoPlaceToPutATime]），整个 `domain/play` 不认时钟由
 *    [PlayStaysClocklessTest] 守着。AC-11.2、AC-12.2、AC-12.3 在这里是结构性地做不到。
 * 4. **判据是不是真的按位置算**。[oddIndexes] 是**构造出来的下标**（[OddOne.deal] 先洗出
 *    下标集合，再按集合摆字），不是摆好之后按字数出来的；[oddIndexesAreExactlyTheCellsOfTheRareWord]
 *    两个方向都查一遍，把这两种算法钉在一起。
 */
class OddOneTest {

    private fun round(seed: Int): OddOneRound = OddOne.deal(Random(seed))

    /** 盘上出现十次的那个字（大多数格子上的字）。 */
    private fun commonWord(dealt: OddOneRound): String =
        dealt.cells.groupingBy { it }.eachCount().maxByOrNull { it.value }!!.key

    /** 盘上出现两次的那个字，也就是「跟别人不一样」的那个。 */
    private fun oddWord(dealt: OddOneRound): String = dealt.cells[dealt.oddIndexes.first()]

    /** 盘上一个**不是**「不一样」的格子。 */
    private fun commonIndex(dealt: OddOneRound): Int =
        dealt.cells.indices.first { it !in dealt.oddIndexes }

    /** 盘上一个「不一样」的格子。 */
    private fun anOddIndex(dealt: OddOneRound): Int = dealt.oddIndexes.first()

    // ---- 出局：这一局是什么 ----

    @Test
    fun dealIsAPureFunctionOfTheSeed() {
        assertEquals("同一个种子摆出了两局不一样的盘，测试就钉不住局面了", round(7), round(7))
    }

    @Test
    fun theBoardIsExactlyTwoWordsWorthOfCells() {
        for (seed in 0..49) {
            val dealt = round(seed)
            val counts = dealt.cells.groupingBy { it }.eachCount()
            assertEquals(
                "种子 $seed 的盘上不是两个字：$counts，那就不是「找一个不一样的」",
                2,
                counts.size,
            )
            assertEquals(
                "跟别人不一样的格子不是 ${OddOne.ODD_COUNT} 个",
                OddOne.ODD_COUNT,
                counts[oddWord(dealt)] ?: 0,
            )
            assertEquals(
                "一样的格子数不是「格子数减去不一样的格数」",
                OddOne.CELLS - OddOne.ODD_COUNT,
                counts[commonWord(dealt)] ?: 0,
            )
            assertNotEquals(
                "两个词碰巧是同一个字，盘上就分不出谁不一样了（种子 $seed）",
                commonWord(dealt),
                oddWord(dealt),
            )
        }
    }

    @Test
    fun oddIndexesAreExactlyTheCellsOfTheRareWord() {
        // [oddIndexes] 是**构造出来的下标**（[OddOne.deal] 里先定下哪几格不一样，再按这个集合摆字），
        // 不是摆好之后按字数一数谁少。这里两个方向都查，把两种算法钉在一起：只要它们一致，
        // 「构造式」就没有偏离「按字数」，而万一将来两个词重了，这条会先红。
        for (seed in 0..49) {
            val dealt = round(seed)
            val rare = oddWord(dealt)
            for (index in dealt.cells.indices) {
                val cellIsOdd = dealt.cells[index] == rare
                val indexIsOdd = index in dealt.oddIndexes
                assertEquals(
                    "第 $index 格的「${dealt.cells[index]}」与 oddIndexes 对不上（种子 $seed）",
                    cellIsOdd,
                    indexIsOdd,
                )
            }
        }
    }

    @Test
    fun theBoardIsNotAlwaysInTheSameOrder() {
        for (seed in 0..29) {
            if (round(seed).oddIndexes != round(seed + 100).oddIndexes) return
        }
        throw AssertionError("「不一样」的两格几乎总在同一个位置：换个种子还是那两格")
    }

    @Test
    fun onlyWordsFromTheTableEverShowUp() {
        val known = CardWords.all.toSet()
        for (seed in 0..49) {
            for (word in round(seed).cells) {
                assertTrue("种子 $seed 的盘上摆着字表以外的「$word」", word in known)
            }
        }
    }

    @Test
    fun aFreshRoundStartsWithNothingFound() {
        for (seed in 0..19) {
            val dealt = round(seed)
            assertTrue("还没点一下就有点出来的格子了", dealt.found.isEmpty())
            assertFalse("还没点一个就说结束了", dealt.finished)
            assertEquals(PlayHint.NONE, dealt.hint)
            assertTrue(
                "一进去就有格子被标记了",
                dealt.cells.indices.all { dealt.markOf(it) == CellMark.PLAIN },
            )
        }
    }

    @Test
    fun itTakesTwoCorrectTapsToFinish() {
        val dealt = round(1)
        assertEquals(OddOne.ODD_COUNT, dealt.stepsToFinish)
        assertEquals("要点的两格却要说点两下以上", dealt.oddIndexes.size, dealt.stepsToFinish)
    }

    // ---- 点对了就少一个 ----

    @Test
    fun tappingAnOddCellMarksItAndKeepsGoing() {
        val dealt = round(3)
        val index = anOddIndex(dealt)
        val after = dealt.tap(index)
        assertEquals(PlayHint.NONE, after.hint)
        assertEquals("点对了没记下来", setOf(index), after.found)
        assertEquals("点对的那一格没有压暗", CellMark.DONE, after.markOf(index))
        assertFalse(after.finished)
        assertEquals("点对了格子却被改了", dealt.cells, after.cells)
    }

    @Test
    fun tappingTheTwoOddCellsFinishesTheRound() {
        val dealt = round(5)
        var current: OddOneRound = dealt
        val order = dealt.oddIndexes.toList()
        order.forEachIndexed { i, index ->
            assertFalse("第 ${i + 1} 下之前就说结束了", current.finished)
            current = current.tap(index)
        }
        assertTrue("两格都点出来了却还说没结束", current.finished)
        assertEquals(OddOne.ODD_COUNT, current.found.size)
        assertEquals("完事之后不该还留着提示", PlayHint.NONE, current.hint)
        assertTrue(
            "完事之后那两格还有没压暗的",
            dealt.oddIndexes.all { current.markOf(it) == CellMark.DONE },
        )
    }

    // ---- 点错的全部后果就是一句话 ----

    @Test
    fun tappingACommonCellOnlyChangesTheHint() {
        val dealt = round(8)
        val after = dealt.tap(commonIndex(dealt))
        assertEquals(PlayHint.NOT_THIS_ONE, after.hint)
        assertEquals("点错了竟然被记下来了", 0, after.found.size)
        assertEquals("点错了盘面被改了", dealt.cells, after.cells)
        assertFalse(after.finished)
        assertNotEquals(dealt, after)
    }

    @Test
    fun wrongTapsNeverAccumulateAnything() {
        val dealt = round(11)
        val wrong = commonIndex(dealt)
        var after: OddOneRound = dealt
        repeat(10) { after = after.tap(wrong) }
        // 除了那一句提示，这一局和刚开局时一模一样：**连「点过哪一格」都没记**，
        // 更没有地方记下「错了几次」——因为 OddOneRound 上根本没有能放这个数的字段。
        assertEquals(dealt.copy(hint = PlayHint.NOT_THIS_ONE), after)
        assertEquals("错点累积出了进展", 0, after.found.size)
    }

    @Test
    fun tappingAnAlreadyFoundCellSaysSo() {
        val dealt = round(9)
        val index = anOddIndex(dealt)
        val after = dealt.tap(index).tap(index)
        assertEquals("重复点同一个没说清是重复", PlayHint.ALREADY_DONE, after.hint)
        assertEquals("重复点把已经点出来的弄丢了", setOf(index), after.found)
    }

    @Test
    fun aWrongTapDoesNotUndoWhatWasFound() {
        val dealt = round(13)
        val first = anOddIndex(dealt)
        val afterOne = dealt.tap(first)
        val afterWrong = afterOne.tap(commonIndex(afterOne))
        assertEquals("点错把已经点出来的退回去了", 1, afterWrong.found.size)
        assertEquals(PlayHint.NOT_THIS_ONE, afterWrong.hint)
        // 已经点出来的不动，所以接着点别的那一格照样能成——用户不需要先把错的那下撤掉。
        val second = dealt.oddIndexes.first { it != first }
        val afterSecond = afterWrong.tap(second)
        assertEquals("点错之后接着点对的，没能往前走", 2, afterSecond.found.size)
        assertTrue("两格都点出来了却还说没结束", afterSecond.finished)
        assertEquals("完事之后不该还留着提示", PlayHint.NONE, afterSecond.hint)
    }

    // ---- 边界：点完之后、格子之外 ----

    @Test
    fun tappingAfterItIsFinishedIsIgnored() {
        var done: OddOneRound = round(17)
        done.oddIndexes.toList().forEach { done = done.tap(it) }
        assertTrue(done.finished)
        assertSame("已经点完了还能继续点", done, done.tap(0))
        assertSame(done, done.tap(-1))
        assertSame(done, done.tap(9_999))
    }

    @Test
    fun tappingOutsideTheBoardIsIgnored() {
        val dealt = round(2)
        assertSame("越界的下标被当成了有效点击", dealt, dealt.tap(-1))
        assertSame(dealt, dealt.tap(dealt.cells.size))
        assertSame(dealt, dealt.tap(9_999))
        assertEquals("越界的位置不该有任何标记", CellMark.PLAIN, dealt.markOf(99))
        assertEquals("越界的位置不该有字", "", dealt.faceOf(99))
    }

    @Test
    fun markOfIsDoneOrPlainNeverPicked() {
        val dealt = round(4)
        val index = anOddIndex(dealt)
        val after = dealt.tap(index)
        assertEquals("点出来的那一格不是暗的", CellMark.DONE, after.markOf(index))
        assertTrue(
            "点对之后别的格子也变了样",
            after.cells.indices.filter { it != index }.all { after.markOf(it) == CellMark.PLAIN },
        )
        // 这个游戏刻意不用 PICKED：点中一格**当场就了了**，没有「选中了还没成事」这个中间态。
        for (state in listOf(dealt, after, dealt.tap(commonIndex(dealt)))) {
            assertTrue(
                "出现了 PICKED——这个游戏没有中间态（理由见 OddOne 里的类 KDoc）",
                state.cells.indices.all { state.markOf(it) != CellMark.PICKED },
            )
        }
    }

    // ---- 它没有偷偷长出别的东西 ----

    @Test
    fun theRoundKeepsNoPlaceToPutATime() {
        val names = OddOneRound::class.java.declaredFields.map { it.name }
        assertTrue(
            "OddOneRound 的字段清单变了（现在是 $names），这条「没有地方放用时」的断言得重写",
            names.containsAll(listOf("gameId", "cells", "oddIndexes", "found", "hint")),
        )
        val timeLike = listOf("time", "second", "milli", "duration", "elapsed", "clock", "started")
        for (name in names) {
            val lower = name.lowercase()
            for (word in timeLike) {
                assertFalse(
                    "OddOneRound 上有一个像时间的字段 `$name`——计时就是从这种字段开始的（AC-11.2）",
                    lower.contains(word),
                )
            }
        }
    }

    // ---- 布局与上限：界面和 AR 都从这里取数 ----

    @Test
    fun theRoundMatchesTheLayoutTheScreenAssumes() {
        val dealt = round(10)
        assertEquals(OddOne.ID, OddOne.game.id)
        assertEquals("这一局挂的不是自己的 id", OddOne.ID, dealt.gameId)
        assertEquals(OddOne.COLUMNS, OddOne.game.columns)
        assertEquals("盘面格数不是游戏说的那个数", OddOne.CELLS, dealt.cells.size)
        assertEquals(
            "格子数装不满整行，最后一行会缺一格",
            0,
            dealt.cells.size % OddOne.game.columns,
        )
    }

    @Test
    fun oneRoundStaysInsideThreeMinutes() {
        assertEquals(60, OddOne.game.longestSeconds)
        assertTrue("一局的上限超过了 3 分钟（AC-11.3）", OddOne.game.longestSeconds <= 180)
    }

    // ---- 名字与说明 ----

    @Test
    fun theGameSaysHowToPlayItWithoutBoasting() {
        assertEquals("找一个不一样的", OddOne.game.name)
        assertTrue(
            "玩法说明只有 ${OddOne.game.howTo.length} 个字，照它玩不起来",
            OddOne.game.howTo.length >= 10,
        )
        assertTrue("玩法说明里没有说「不一样」这回事", OddOne.game.howTo.contains("不一样"))
    }
}
