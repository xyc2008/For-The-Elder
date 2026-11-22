package com.fortheelder.domain.play

import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 找同类：单元测试（JVM，纯值）。
 *
 * 第五个游戏进来之后，这一层的测试要回答四个问题：
 *
 * 1. **这一局能不能玩**。摆了三类东西、要找的那一类在盘上齐齐全全、点一个少一个、
 *    点齐了才算完。
 * 2. **它有没有被前四个游戏带出别的东西**。算一算的「答完就换题」、找一样的「选中了
 *    还没配上」在这里都不是一回事：[SameKindRound.markOf] 只会是 `DONE` 或 `PLAIN`，
 *    从来没有 `PICKED`。
 * 3. **它有没有偷偷开始计时或记分**。`SameKindRound` 上根本没有像时间的字段
 *    （见 [theRoundKeepsNoPlaceToPutATime]），整个 `domain/play` 不认时钟由
 *    [PlayStaysClocklessTest] 守着。AC-11.2、AC-12.2、AC-12.3 在这里是结构性地做不到。
 * 4. **判据是不是真的按「类」算**。[targets] 是**构造出来的下标**，不是按字反查的类别；
 *    [targetsAreExactlyTheCellsOfTheTargetKind] 两个方向都查一遍，把这两种算法钉在一起。
 */
class SameKindTest {

    private fun round(seed: Int): SameKindRound = SameKind.deal(Random(seed))

    /** 那一局要找的类别。 */
    private fun targetKind(round: SameKindRound): Kind =
        KindWords.kinds.single { it.label == round.kindLabel }

    /** 盘上一个**不是**要找那一类的格子。 */
    private fun wrongIndex(round: SameKindRound): Int =
        round.cells.indices.first { it !in round.targets }

    /** 盘上一个要找那一类的格子。 */
    private fun aTargetIndex(round: SameKindRound): Int = round.targets.first()

    private fun foundWords(round: SameKindRound): List<String> =
        round.found.map { round.cells[it] }.sorted()

    // ---- 出局：这一局是什么 ----

    @Test
    fun dealIsAPureFunctionOfTheSeed() {
        assertEquals("同一个种子摆出了两局不一样的盘，测试就钉不住局面了", round(7), round(7))
    }

    @Test
    fun theBoardIsThreeKindsWorthOfCells() {
        for (seed in 0..49) {
            val dealt = round(seed)
            assertEquals(
                "种子 $seed 下的格子数不是 ${SameKind.KINDS_ON_BOARD} 类 × ${SameKind.PER_KIND} 个",
                SameKind.KINDS_ON_BOARD * SameKind.PER_KIND,
                dealt.cells.size,
            )
            assertEquals(
                "种子 $seed 下有一样的格子：${dealt.cells}，划不出哪一个是哪一类",
                dealt.cells.size,
                dealt.cells.toSet().size,
            )
            assertEquals(
                "格子数装不满整行，最后一行会缺一格",
                0,
                dealt.cells.size % SameKind.game.columns,
            )
        }
    }

    @Test
    fun theTargetKindIsFullyOnTheBoard() {
        for (seed in 0..19) {
            val dealt = round(seed)
            val kind = targetKind(dealt)
            assertEquals(
                "「${kind.label}」这一类有 ${kind.words.size} 个，要找的那一类却不是 ${SameKind.PER_KIND} 个",
                SameKind.PER_KIND,
                dealt.targets.size,
            )
            assertEquals(
                "要找的「${kind.label}」在盘上摆的不是那几个词",
                kind.words.sorted(),
                dealt.targets.map { dealt.cells[it] }.sorted(),
            )
        }
    }

    @Test
    fun targetsAreExactlyTheCellsOfTheTargetKind() {
        // [targets] 是**构造出来的下标**（[SameKind.deal] 里每一格都带着自己的来源类），
        // 不是摆好之后再按字反查类别。这里两个方向都查，把两种算法钉在一起：只要它们一致，
        // 「构造式」就没有偏离「按字查」，而万一将来两类的词重了，这条会先红。
        for (seed in 0..49) {
            val dealt = round(seed)
            val words = targetKind(dealt).words.toSet()
            for (index in dealt.cells.indices) {
                val cellIsTarget = dealt.cells[index] in words
                val indexIsTarget = index in dealt.targets
                assertEquals(
                    "第 $index 格的「${dealt.cells[index]}」与 targets 对不上（种子 $seed）",
                    cellIsTarget,
                    indexIsTarget,
                )
            }
        }
    }

    @Test
    fun theOtherCellsBelongToOtherKinds() {
        val owner: Map<String, String> =
            KindWords.kinds.flatMap { k -> k.words.map { it to k.label } }.toMap()
        for (seed in 0..19) {
            val dealt = round(seed)
            val targetLabel = dealt.kindLabel
            for (index in dealt.cells.indices) {
                if (index in dealt.targets) continue
                val label = owner[dealt.cells[index]]
                assertTrue("第 $index 格的「${dealt.cells[index]}」不在任何一类里", label != null)
                assertNotEquals(
                    "第 $index 格不该是目标类的，却被摆在了非目标位上（种子 $seed）",
                    targetLabel,
                    label,
                )
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
    fun itTakesFourCorrectTapsToFinish() {
        val dealt = round(1)
        assertEquals(SameKind.PER_KIND, dealt.stepsToFinish)
        assertEquals("要找的四格却要说点四下以上", dealt.targets.size, dealt.stepsToFinish)
    }

    // ---- 点对了就少一个 ----

    @Test
    fun tappingATargetMarksItAndKeepsGoing() {
        val dealt = round(3)
        val index = aTargetIndex(dealt)
        val after = dealt.tap(index)
        assertEquals(PlayHint.NONE, after.hint)
        assertEquals("点对了没记下来", setOf(index), after.found)
        assertEquals("点对的那一格没有压暗", CellMark.DONE, after.markOf(index))
        assertFalse(after.finished)
        assertEquals("点对了格子却被改了", dealt.cells, after.cells)
    }

    @Test
    fun fourTargetTapsFinishTheRound() {
        var current: SameKindRound = round(5)
        val order = current.targets.toList()
        order.forEachIndexed { i, index ->
            assertFalse("第 ${i + 1} 下之前就说结束了", current.finished)
            current = current.tap(index)
        }
        assertTrue("要找的都点出来了却还说没结束", current.finished)
        assertEquals(SameKind.PER_KIND, current.found.size)
        assertEquals("完事之后不该还留着提示", PlayHint.NONE, current.hint)
        assertTrue(
            "完事之后盘上还有格子不是暗的",
            current.targets.all { current.markOf(it) == CellMark.DONE },
        )
    }

    // ---- 点错的全部后果就是一句话 ----

    @Test
    fun tappingAWrongCellOnlyChangesTheHint() {
        val dealt = round(8)
        val after = dealt.tap(wrongIndex(dealt))
        assertEquals(PlayHint.NOT_THIS_ONE, after.hint)
        assertEquals("点错了竟然被记下来了", 0, after.found.size)
        assertEquals("点错了盘面被改了", dealt.cells, after.cells)
        assertFalse(after.finished)
        assertNotEquals(dealt, after)
    }

    @Test
    fun wrongTapsNeverAccumulateAnything() {
        val dealt = round(11)
        val wrong = wrongIndex(dealt)
        var after: SameKindRound = dealt
        repeat(10) { after = after.tap(wrong) }
        // 除了那一句提示，这一局和刚开局时一模一样：**连「点过哪一格」都没记**，
        // 更没有地方记下「错了几次」——因为 SameKindRound 上根本没有能放这个数的字段。
        assertEquals(dealt.copy(hint = PlayHint.NOT_THIS_ONE), after)
        assertEquals("错点累积出了进展", 0, after.found.size)
    }

    @Test
    fun tappingAnAlreadyFoundCellSaysSo() {
        val dealt = round(9)
        val index = aTargetIndex(dealt)
        val after = dealt.tap(index).tap(index)
        assertEquals("重复点同一个没说清是重复", PlayHint.ALREADY_DONE, after.hint)
        assertEquals("重复点把已经点出来的弄丢了", setOf(index), after.found)
    }

    @Test
    fun aWrongTapDoesNotUndoWhatWasFound() {
        val dealt = round(13)
        val first = aTargetIndex(dealt)
        val afterOne = dealt.tap(first)
        val afterWrong = afterOne.tap(wrongIndex(afterOne))
        assertEquals("点错把已经点出来的退回去了", 1, afterWrong.found.size)
        assertEquals(PlayHint.NOT_THIS_ONE, afterWrong.hint)
        // 已经点出来的不动，所以接着点别的照样能成——用户不需要先把错的那下撤掉。
        val second = dealt.targets.first { it != first }
        assertEquals("点错之后接着点对的，没能往前走", 2, afterWrong.tap(second).found.size)
    }

    // ---- 边界：点完之后、格子之外 ----

    @Test
    fun tappingAfterItIsFinishedIsIgnored() {
        var done: SameKindRound = round(17)
        done.targets.toList().forEach { done = done.tap(it) }
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
        val index = aTargetIndex(dealt)
        val after = dealt.tap(index)
        assertEquals("点出来的那一格不是暗的", CellMark.DONE, after.markOf(index))
        assertTrue(
            "点错之后别的格子也变了样",
            after.cells.indices.filter { it != index }.all { after.markOf(it) == CellMark.PLAIN },
        )
        // 这个游戏刻意不用 PICKED：点中一格**当场就了了**，没有「选中了还没成事」这个中间态。
        for (state in listOf(dealt, after, dealt.tap(wrongIndex(dealt)))) {
            assertTrue(
                "出现了 PICKED——这个游戏没有中间态（理由见 SameKind 里的类 KDoc）",
                state.cells.indices.all { state.markOf(it) != CellMark.PICKED },
            )
        }
    }

    // ---- 它没有偷偷长出别的东西 ----

    @Test
    fun theRoundKeepsNoPlaceToPutATime() {
        val names = SameKindRound::class.java.declaredFields.map { it.name }
        assertTrue(
            "SameKindRound 的字段清单变了（现在是 $names），这条「没有地方放用时」的断言得重写",
            names.containsAll(listOf("gameId", "cells", "kindLabel", "targets", "found", "hint")),
        )
        val timeLike = listOf("time", "second", "milli", "duration", "elapsed", "clock", "started")
        for (name in names) {
            val lower = name.lowercase()
            for (word in timeLike) {
                assertFalse(
                    "SameKindRound 上有一个像时间的字段 `$name`——计时就是从这种字段开始的（AC-11.2）",
                    lower.contains(word),
                )
            }
        }
    }

    // ---- 布局与上限：界面和 AR 都从这里取数 ----

    @Test
    fun theRoundMatchesTheLayoutTheScreenAssumes() {
        val dealt = round(10)
        assertEquals(SameKind.ID, SameKind.game.id)
        assertEquals("这一局挂的不是自己的 id", SameKind.ID, dealt.gameId)
        assertEquals(SameKind.COLUMNS, SameKind.game.columns)
        assertEquals(
            "格子数装不满整行，最后一行会缺一格",
            0,
            dealt.cells.size % SameKind.game.columns,
        )
    }

    @Test
    fun oneRoundStaysInsideThreeMinutes() {
        assertEquals(120, SameKind.game.longestSeconds)
        assertTrue("一局的上限超过了 3 分钟（AC-11.3）", SameKind.game.longestSeconds <= 180)
    }

    // ---- 局面会变：不能总问同一类、总摆同一个顺序 ----

    @Test
    fun theTargetKindIsNotAlwaysTheSame() {
        val labels = (0..29).map { round(it).kindLabel }.toSet()
        assertTrue(
            "30 局里要找的总是 $labels——用户摸清套路就不用看了",
            labels.size >= 2,
        )
    }

    @Test
    fun theBoardIsNotAlwaysInTheSameOrder() {
        for (seed in 0..29) {
            if (round(seed).cells != round(seed + 100).cells) return
        }
        throw AssertionError("格子的摆法几乎不变：换个种子还是同一个顺序")
    }

    @Test
    fun onlyKindsFromTheTableEverShowUp() {
        val known = KindWords.kinds.map { it.label }.toSet()
        for (seed in 0..49) {
            assertTrue("种子 $seed 下要找的「${round(seed).kindLabel}」不在字表里", round(seed).kindLabel in known)
        }
    }

    // ---- 名字与说明 ----

    @Test
    fun theGameSaysHowToPlayItWithoutBoasting() {
        assertEquals("找同类", SameKind.game.name)
        assertTrue(
            "玩法说明只有 ${SameKind.game.howTo.length} 个字，照它玩不起来",
            SameKind.game.howTo.length >= 10,
        )
        assertTrue("玩法说明里没有说要找哪一类", SameKind.game.howTo.contains("哪一类"))
    }
}
