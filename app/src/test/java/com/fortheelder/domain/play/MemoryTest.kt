package com.fortheelder.domain.play

import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 记一记：单元测试（JVM，纯值）。
 *
 * 这是第四个游戏，也是**第一个「哪些字能给用户看」不由 `cells` 一个人说了算的游戏**：
 * 盖着的牌，字还在 [MemoryRound.cells] 里，可屏幕上写的是 [Memory.BACK]。所以这一层除了
 * 前三个游戏那三个老问题，还要多答一个问题：
 *
 * 1. **这一局能不能玩完**。12 张牌、六个字各两张、开局全盖着、点一张翻一张、两张一样
 *    收起来、六对配完就收尾，一下不多一下不少（[pairingEveryPairEndsTheRoundWithNothingFaceUp]）。
 * 2. **盖着的牌会不会漏字**。开局十二格全是 [Memory.BACK]；翻开的才露字；盖回去的又变回
 *    [Memory.BACK]（[theRoundStartsWithEverythingFaceDown]、[aTapAfterMissedPutsBothBackAndKeepsPairs]）。
 * 3. **「不是一对」会不会变成两个互相矛盾的字段**。[MemoryOpening] 是三选一的 sealed，
 *    「先翻的那张」和「等着盖回去的一对」不可能同时成立。这是第 41 轮做成 sealed 的全部理由
 *    （`records/03` ADR-038），`when` 少写一支编译不过。
 * 4. **它有没有偷偷开始计时、记分、记翻牌次数**。`MemoryRound` 上既没有像时间的字段，也没有
 *    能放计数的字段（[theRoundKeepsNoPlaceToPutATime]、[theRoundKeepsNoPlaceToPutAClickCount]），
 *    而且十次点错之后这一局与开局**逐字段相等**（[wrongTapsNeverAccumulateAnything]）。
 *    AC-11.2、AC-12.2、AC-12.3 在这里是结构性地做不到，不是靠自觉。
 *
 * 「牌背为什么写两个字而不是写个符号」由 [theBackIsAWordAndNotASymbol] 守着。
 * 名字与玩法说明要过宣称词词表，那一条**不在这里重复**：`ClaimWordsTest` 是照
 * `PlayCatalog.games` 逐条扫的，第四个游戏挂进目录的那一刻就已经被扫进去了——
 * 同一件事有两处出处，早晚会出现两处不一致。
 */
class MemoryTest {

    private fun round(seed: Int): MemoryRound = Memory.deal(Random(seed))

    /** 一局里写着 [word] 这一字的两个位置。牌面成对摆，所以恰好两个。 */
    private fun pairOf(round: MemoryRound, word: String): Pair<Int, Int> {
        val spots = round.cells.indices.filter { round.cells[it] == word }
        assertEquals("「$word」在这盘上不是两张，配不成一对", 2, spots.size)
        return spots[0] to spots[1]
    }

    /** 一局里任意两格**不同的字**，而且都还没收起来。点错用的是它们。 */
    private fun twoDifferent(round: MemoryRound): Pair<Int, Int> {
        val free = round.cells.indices.filter { it !in round.solved }
        val first = free.first()
        val second = free.first { round.cells[it] != round.cells[first] }
        return first to second
    }

    /** 从 [start] 一路配到底：每对翻两下。 */
    private fun playThrough(start: MemoryRound): MemoryRound {
        var play = start
        for (word in start.cells.distinct()) {
            val (a, b) = pairOf(play, word)
            play = play.tap(a).tap(b)
        }
        return play
    }

    // ---- 出局：这一局是什么 ----

    @Test
    fun dealIsAPureFunctionOfTheSeed() {
        assertEquals("同一个种子摆出了两局不一样的牌，测试就钉不住局面了", round(7), round(7))
    }

    @Test
    fun everyWordAppearsExactlyTwice() {
        for (seed in 0..19) {
            val dealt = round(seed)
            assertEquals("种子 $seed 下的牌数不对", Memory.PAIRS * 2, dealt.cells.size)
            assertEquals(
                "种子 $seed 下不同字的个数不对，六对配不齐",
                Memory.PAIRS,
                dealt.cells.toSet().size,
            )
            for (word in dealt.cells.toSet()) {
                assertEquals(
                    "种子 $seed 下「$word」写了 ${dealt.cells.count { it == word }} 张，配不成一对",
                    2,
                    dealt.cells.count { it == word },
                )
            }
        }
    }

    @Test
    fun theBoardOnlyEverShowsCardWords() {
        for (seed in 0..19) {
            for (word in round(seed).cells) {
                assertTrue(
                    "种子 $seed 下摆出了一个不在词池里的字「$word」，这个字没有任何地方审过",
                    CardWords.all.contains(word),
                )
            }
        }
    }

    @Test
    fun theRoundStartsWithEverythingFaceDown() {
        val dealt = round(1)
        assertEquals(MemoryOpening.Closed, dealt.opening)
        assertEquals(PlayHint.NONE, dealt.hint)
        assertFalse("一进去就已经结束了", dealt.finished)
        assertTrue("开局就有格子收起来了", dealt.solved.isEmpty())
        assertEquals("六对配完要做对十二下", 12, dealt.stepsToFinish)
        assertEquals(Memory.PAIRS, dealt.pairs)
        for (index in dealt.cells.indices) {
            assertEquals("第 $index 格开局的标记不对", CellMark.PLAIN, dealt.markOf(index))
            assertEquals("第 $index 格开局就把字给用户看了", Memory.BACK, dealt.faceOf(index))
        }
    }

    @Test
    fun theRoundMatchesTheLayoutTheScreenAssumes() {
        val dealt = round(10)
        assertEquals(Memory.ID, Memory.game.id)
        assertEquals("这一局挂的不是自己的 id", Memory.ID, dealt.gameId)
        assertEquals(
            "牌数装不满整行，最后一行会缺几格",
            0,
            dealt.cells.size % Memory.game.columns,
        )
        assertEquals(Memory.COLUMNS, Memory.game.columns)
        assertEquals("12 张牌 4 列该摆成 3 行", 3, dealt.cells.size / Memory.game.columns)
    }

    @Test
    fun theFourthGameIsTheOneTheCatalogHandsOut() {
        val listed = PlayCatalog.games.first { it.id == Memory.ID }
        assertSame("目录里挂的记一记不是这一个", Memory.game, listed)
        assertEquals("目录里挂的摆法不是这一个", round(0), listed.deal(Random(0)))
    }

    // ---- 翻牌：点一下会发生什么 ----

    @Test
    fun openingOneCardShowsItAndMarksItPicked() {
        val dealt = round(2)
        val (a, b) = pairOf(dealt, dealt.cells[0])
        val opened = dealt.tap(a)
        assertEquals(MemoryOpening.Single(a), opened.opening)
        assertEquals("翻开的那张没把字给用户看", dealt.cells[a], opened.faceOf(a))
        assertEquals("翻开的那张没有一样看得出来的样子", CellMark.PICKED, opened.markOf(a))
        assertEquals(
            "同一个字的另一张也跟着露了字，等于直接把答案送给用户",
            Memory.BACK,
            opened.faceOf(b),
        )
        assertEquals("另一张的标记也变了", CellMark.PLAIN, opened.markOf(b))
        assertEquals(PlayHint.NONE, opened.hint)
        assertFalse("才翻开一张就说这一局完了", opened.finished)
        assertTrue("翻开一张不该算收起来", opened.solved.isEmpty())
    }

    @Test
    fun matchingTwoCardsPutsThePairAway() {
        val dealt = round(3)
        val (a, b) = pairOf(dealt, dealt.cells[0])
        val done = dealt.tap(a).tap(b)
        assertEquals("配上的那一对没收起来", setOf(a, b), done.solved)
        assertEquals("收起来之后还等着翻第二张", MemoryOpening.Closed, done.opening)
        assertEquals(PlayHint.NONE, done.hint)
        assertEquals(CellMark.DONE, done.markOf(a))
        assertEquals(CellMark.DONE, done.markOf(b))
        assertEquals("收起来的那两张该一直露着字", dealt.cells[a], done.faceOf(a))
        assertEquals(dealt.cells[b], done.faceOf(b))
        assertFalse("六对才配了一对就说完了", done.finished)
    }

    @Test
    fun twoDifferentCardsAreMissedAndSaySo() {
        val dealt = round(4)
        val (a, b) = twoDifferent(dealt)
        assertNotEquals("这两格的字其实一样，点下去只会配上", dealt.cells[a], dealt.cells[b])
        val missed = dealt.tap(a).tap(b)
        assertEquals(MemoryOpening.Missed(first = a, second = b), missed.opening)
        assertEquals("点错没有说话", PlayHint.MISSED_PAIR, missed.hint)
        assertEquals(
            "点错的这两张也该露着字，用户得看得见自己点的是什么",
            dealt.cells[a],
            missed.faceOf(a),
        )
        assertEquals(dealt.cells[b], missed.faceOf(b))
        assertEquals(CellMark.PICKED, missed.markOf(a))
        assertEquals(CellMark.PICKED, missed.markOf(b))
        assertTrue("点错居然算成了收起来", missed.solved.isEmpty())
        assertFalse(missed.finished)
    }

    @Test
    fun aTapAfterMissedPutsBothBackAndKeepsPairs() {
        val dealt = round(5)
        val held = pairOf(dealt, dealt.cells[0])
        val onePairAway = dealt.tap(held.first).tap(held.second)
        val (a, b) = twoDifferent(onePairAway)
        val missed = onePairAway.tap(a).tap(b)

        // 「等着盖回去」这一局面里，点哪一格都只做「盖回去」这一件事。
        val free = missed.cells.indices.first { it !in missed.solved }
        val backDown = missed.tap(free)
        assertEquals("点一下之后那两张没盖回去", MemoryOpening.Closed, backDown.opening)
        assertEquals("盖回去之后还露着字", Memory.BACK, backDown.faceOf(a))
        assertEquals(Memory.BACK, backDown.faceOf(b))
        assertEquals("盖回去之后样子没还原", CellMark.PLAIN, backDown.markOf(a))
        assertEquals(CellMark.PLAIN, backDown.markOf(b))
        assertEquals("盖回去之后还挂着那句话", PlayHint.NONE, backDown.hint)
        assertEquals("盖回去把已经配好的一对也掀了", onePairAway.solved, backDown.solved)
        assertEquals(
            "盖回去之后再点一下该能翻开新的一张",
            MemoryOpening.Single(free),
            backDown.tap(free).opening,
        )

        // 已经收起来的牌，之后无论什么时候点，都只说一句「这个已经点过了」。
        val already = backDown.tap(held.first)
        assertEquals("点已经收起来的那张没有说话", PlayHint.ALREADY_DONE, already.hint)
        assertEquals("点收起来的牌把局面改掉了", MemoryOpening.Closed, already.opening)
        assertEquals(onePairAway.solved, already.solved)
    }

    @Test
    fun tappingTheSameCardTwiceSaysAlreadyDone() {
        val dealt = round(6)
        val (a, _) = pairOf(dealt, dealt.cells[0])
        val twice = dealt.tap(a).tap(a)
        assertEquals(PlayHint.ALREADY_DONE, twice.hint)
        assertEquals("点同一张两次把牌翻了回去", MemoryOpening.Single(a), twice.opening)
        assertEquals(dealt.cells[a], twice.faceOf(a))
        assertEquals(CellMark.PICKED, twice.markOf(a))
    }

    @Test
    fun wrongTapsNeverAccumulateAnything() {
        val dealt = round(8)
        var play = dealt
        repeat(10) { attempt ->
            val (a, b) = twoDifferent(play)
            val missed = play.tap(a).tap(b)
            assertEquals("第 ${attempt + 1} 次点错没有说话", PlayHint.MISSED_PAIR, missed.hint)
            play = missed.tap(a)
        }
        // 十次点错之后这一局与开局**逐字段相等**：没有错几次、没有翻几下、没有半点进度。
        // 这不是「我们没记」，是这个值里根本没有地方能记（AC-12.3）。
        assertEquals("十次点错之后这一局跟开局不一样了，说明有点错留下的东西", dealt, play)
    }

    @Test
    fun pairingEveryPairEndsTheRoundWithNothingFaceUp() {
        for (seed in 0..9) {
            val dealt = round(seed)
            val words = dealt.cells.distinct()
            var play = dealt
            var taps = 0
            words.dropLast(1).forEach { word ->
                val (a, b) = pairOf(play, word)
                play = play.tap(a).tap(b)
                taps += 2
                assertFalse("种子 $seed 下最后一对还没配完就说这一局完了", play.finished)
            }
            val (a, b) = pairOf(play, words.last())
            play = play.tap(a).tap(b)
            taps += 2

            assertTrue("种子 $seed 下六对都配完了还说没完", play.finished)
            assertEquals("六对配完该是 ${play.stepsToFinish} 下，一下不多一下不少", play.stepsToFinish, taps)
            assertEquals(12, taps)
            assertEquals("收尾时还有牌摊在明面上", MemoryOpening.Closed, play.opening)
            assertEquals(PlayHint.NONE, play.hint)
            for (index in play.cells.indices) {
                assertEquals("收尾时第 $index 格不是收起来的样子", CellMark.DONE, play.markOf(index))
                assertEquals("收尾时第 $index 格没有露着字", play.cells[index], play.faceOf(index))
            }
        }
    }

    @Test
    fun tappingAfterItIsFinishedIsIgnored() {
        val finished = playThrough(round(11))
        assertTrue(finished.finished)
        for (index in listOf(0, 5, 11)) {
            assertSame("这一局完了之后点第 $index 格居然还有反应", finished, finished.tap(index))
        }
    }

    @Test
    fun tappingOutsideTheBoardIsIgnored() {
        val dealt = round(12)
        assertSame("越界的下标被当成了有效点击", dealt, dealt.tap(-1))
        assertSame(dealt, dealt.tap(dealt.cells.size))
        assertSame(dealt, dealt.tap(9_999))
        assertEquals("越界的位置不该有任何标记", CellMark.PLAIN, dealt.markOf(99))
        assertEquals("越界的位置不该有字", "", dealt.faceOf(99))
    }

    // ---- 它没有偷偷长出别的东西 ----

    @Test
    fun theRoundKeepsNoPlaceToPutATime() {
        val names = MemoryRound::class.java.declaredFields.map { it.name }
        assertTrue(
            "MemoryRound 的字段清单变了（现在是 $names），这条「没有地方放用时」的断言得重写",
            names.containsAll(listOf("gameId", "cells", "solved", "opening", "hint")),
        )
        // 只扫 MemoryRound 自己。MemoryOpening.Missed 里那两个字段叫 first 和 second，
        // 说的是「第几格」，跟秒数没关系——扫进去就成了误报，误报会让守卫被绕过去。
        val timeLike = listOf("time", "second", "milli", "duration", "elapsed", "clock", "started")
        for (name in names) {
            for (word in timeLike) {
                assertFalse(
                    "MemoryRound 上有一个像时间的字段 `$name`——计时就是从这种字段开始的（AC-11.2）",
                    name.lowercase().contains(word),
                )
            }
        }
    }

    @Test
    fun theRoundKeepsNoPlaceToPutAClickCount() {
        val names = MemoryRound::class.java.declaredFields.map { it.name }
        val countLike = listOf(
            "count",
            "score",
            "point",
            "tries",
            "attempt",
            "mistake",
            "wrong",
            "move",
            "rank",
            "level",
            "streak",
        )
        for (name in names) {
            for (word in countLike) {
                assertFalse(
                    "MemoryRound 上有一个像计数的字段 `$name`——翻了几下、错了几次、" +
                        "得了几分都是从这种字段开始的（AC-12.2、AC-12.3）",
                    name.lowercase().contains(word),
                )
            }
        }
    }

    // ---- 牌面与名字 ----

    @Test
    fun theBackIsAWordAndNotASymbol() {
        assertFalse("牌背写的正好是一个牌面，盖着的牌等于把字露了出来", CardWords.all.contains(Memory.BACK))
        assertTrue(
            "牌背写的是「${Memory.BACK}」而不是一句汉字。这两格字是念给看不见屏幕的人听的：" +
                "TalkBack 会把「？」连着十二张念成十二声「问号」，听的人只知道自己撞上了一堵符号墙，" +
                "而「盖着，第二行第三列」才正好是这个人要的信息",
            Memory.BACK.isNotEmpty() && Memory.BACK.all { it in '\u4e00'..'\u9fff' },
        )
    }

    @Test
    fun oneRoundStaysInsideThreeMinutes() {
        assertEquals(180, Memory.game.longestSeconds)
        assertTrue("一局的上限超过了 3 分钟（AC-11.3）", Memory.game.longestSeconds <= 180)
    }

    @Test
    fun theGameSaysHowToPlayItWithoutBoasting() {
        assertEquals("记一记", Memory.game.name)
        assertTrue(
            "玩法说明只有 ${Memory.game.howTo.length} 个字，照它玩不起来",
            Memory.game.howTo.length >= 10,
        )
        assertTrue("玩法说明里没说牌是盖着的", Memory.game.howTo.contains("盖"))
        assertTrue("玩法说明里没说点一下会翻牌", Memory.game.howTo.contains("翻"))
    }
}
