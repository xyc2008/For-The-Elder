package com.fortheelder.ui.play

import com.fortheelder.domain.play.FindPair
import com.fortheelder.domain.play.FindPairRound
import com.fortheelder.domain.play.PlayCatalog
import com.fortheelder.domain.play.PlayHint
import com.fortheelder.domain.play.PlayRound
import com.fortheelder.domain.play.SchulteGrid
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 这一页措辞的单元测试（JVM，纯字符串）。
 *
 * 用户在这一页上真正会误会的不是布局，是用词。所以这里钉住三件事：
 * 一秒的说法不拖泥带水、该做什么说得清楚、以及**结束卡上不出现第二次邀请**
 * （AC-11.3）。最后一条是用一张催促词表扫全部文案做的，而不是只检查那个按钮的名字——
 * 诱导的话很容易从别的地方溜出来。
 *
 * 第 12 轮加了第二个游戏之后，这一页的话按**规则**分支，所以这里也按规则分别钉：
 * 两种局面各说自己的那一句，不会一个游戏的措辞被另一个游戏顺走。
 *
 * 第 22 轮加了一条更靠前的守卫：**开始之前那句说明里不许有兑现不了的硬上限**。
 * 原先写的是「一局最多 N 分钟」，可这一局没有任何东西会在超时后把它停下，
 * 那句「最多」只是一句好听的话（见 [thePromiseMakesNoHardLimitItCannotKeep]）。
 */
class PlayVoiceTest {

    private val game = SchulteGrid.game
    private val round: PlayRound = game.deal(Random(1))

    /** 按顺序点完这一局（只对舒尔特式的一局成立：它按 1、2、3……点）。 */
    private fun playThrough(start: PlayRound): PlayRound {
        var current = start
        for (n in 1..current.stepsToFinish) current = current.tap(current.cells.indexOf(n.toString()))
        return current
    }

    /** 已经找到那一对的一局找一样。返回具体的形状，用例才能问它找到的是哪两个字。 */
    private fun solvedPair(): FindPairRound {
        var current = FindPair.deal(Random(1))
        for (index in current.cells.indices.filter { current.cells[it] == current.pairLabel }) {
            current = current.tap(index)
        }
        return current
    }

    // ---- 用时的说法 ----

    @Test
    fun durationSpellsOutSecondsMinutesAndBoth() {
        assertEquals("45 秒", PlayVoice.duration(45))
        assertEquals("59 秒", PlayVoice.duration(59))
        assertEquals("1 分钟", PlayVoice.duration(60))
        assertEquals("2 分 5 秒", PlayVoice.duration(125))
        assertEquals("3 分钟", PlayVoice.duration(180))
    }

    @Test
    fun durationNeverShowsANegativeNumber() {
        assertEquals("0 秒", PlayVoice.duration(-3))
        assertEquals("0 秒", PlayVoice.duration(0))
    }

    @Test
    fun thePromiseSaysItHonestly() {
        assertEquals(
            "一局大概 3 分钟就完了。不用计时，也没有分数跟谁比；慢一点也不要紧。",
            PlayVoice.promise(SchulteGrid.game),
        )
        assertEquals(
            "一局大概 2 分钟就完了。不用计时，也没有分数跟谁比；慢一点也不要紧。",
            PlayVoice.promise(FindPair.game),
        )
        assertEquals(180, game.longestSeconds)
    }

    @Test
    fun theNumberInThePromiseIsComputedFromTheGame() {
        // 分钟数是算出来的，不是文案里手写死的数字：把上限改成 120 秒，这句话会跟着变成 2 分钟。
        for (game in PlayCatalog.games) {
            val expected = (game.longestSeconds + 59) / 60
            assertTrue(
                "「${PlayVoice.promise(game)}」里的分钟数不是从 longestSeconds = ${game.longestSeconds} 算出来的",
                PlayVoice.promise(game).contains("大概 $expected 分钟"),
            )
        }
        assertTrue(PlayVoice.promise(SchulteGrid.game.copy(longestSeconds = 120)).contains("大概 2 分钟"))
    }

    @Test
    fun thePromiseMakesNoHardLimitItCannotKeep() {
        // 第 22 轮：原文是「一局最多 N 分钟」。可这一局没有任何东西会在超时后把它停下
        // （domain/play 整层不认识时钟，守卫是 PlayStaysClocklessTest），所以那个「最多」
        // 是一句兑现不了的话——用户慢慢点，它就从说明变成他头上的一道催促。
        // 这条用例把话钉住：屏幕上说「大概」；而「不用计时」「没有分数跟谁比」这两条
        // 真的站得住的承诺，一个字都不能少。
        val hardLimitWords = listOf("最多", "上限", "不超过", "不会超过", "保证", "必须")
        for (game in PlayCatalog.games) {
            val promise = PlayVoice.promise(game)
            for (word in hardLimitWords) {
                assertFalse("「$promise」里出现了一句兑现不了的硬话「$word」", promise.contains(word))
            }
            assertTrue("「$promise」里没说清不用计时", promise.contains("不用计时"))
            assertTrue("「$promise」里没说清不比分", promise.contains("没有分数跟谁比"))
        }
    }

    // ---- 该做什么 ----

    @Test
    fun headlineAlwaysNamesTheCurrentTarget() {
        var current = round
        for (n in 1..5) {
            assertEquals("现在点 $n", PlayVoice.headline(current))
            current = current.tap(current.cells.indexOf(n.toString()))
        }
    }

    @Test
    fun headlineSaysSomethingElseForAFindPairRound() {
        val pair = FindPair.deal(Random(1))
        assertEquals("找出一样的那两张", PlayVoice.headline(pair))
        assertFalse("找一样没有先后顺序，不该说「现在点哪个」", PlayVoice.headline(pair).contains("现在点"))
    }

    @Test
    fun hintIsSilentUntilSomethingGoesWrong() {
        assertNull("顺顺当当的时候不该有话说", PlayVoice.hint(round))
        val ahead = round.tap(round.cells.indexOf("25"))
        assertEquals(PlayHint.NOT_YET.text, PlayVoice.hint(ahead))
        assertNull("这一局已经点完了，不该还有提示", PlayVoice.hint(playThrough(round)))
    }

    @Test
    fun theHintComesFromTheOnePlaceThatOwnsIt() {
        // 措辞只在 PlayHint 里写一次，这一层不另编一套：两处各写一遍迟早会说岔。
        var already = round.tap(round.cells.indexOf("1"))
        already = already.tap(already.cells.indexOf("1"))
        assertEquals(PlayHint.ALREADY_DONE.text, PlayVoice.hint(already))
    }

    @Test
    fun theFindPairRoundUsesItsOwnHintWhenTheCardIsWrong() {
        val pair = FindPair.deal(Random(1))
        val wrong = pair.cells.indexOfFirst { it != pair.pairLabel }
        assertEquals(PlayHint.NOT_THIS_ONE.text, PlayVoice.hint(pair.tap(wrong)))
    }

    // ---- 结果与退路 ----

    @Test
    fun doneLineSaysHowManyCellsAndHowLong() {
        val line = PlayVoice.doneLine(playThrough(round), 43)
        assertTrue("结果里没说清点到了几个：$line", line.contains("25"))
        assertTrue("结果里没说这一局用了多久：$line", line.contains("43 秒"))
        assertTrue(line.contains("都点到了"))
    }

    @Test
    fun doneLineForAFindPairRoundNamesThePairAndReportsNoTime() {
        val pair = solvedPair()
        val line = PlayVoice.doneLine(pair, 43)
        assertTrue("结果里没说找到的是哪两个字：$line", line.contains(pair.pairLabel))
        assertFalse("这一局没有在计时，报一个用时就是把不存在的事说出来：$line", line.contains("秒"))
        assertFalse(line.contains("分钟"))
    }

    @Test
    fun doneNoteMatchesWhatThatGameActuallyKeeps() {
        assertEquals("用时只在这里显示，关掉就没了。", PlayVoice.doneNote(round))
        assertFalse(
            "找一样不报用时，那行小字里也就不该提用时",
            PlayVoice.doneNote(FindPair.deal(Random(1))).contains("用时"),
        )
    }

    @Test
    fun theEndCardOffersNoSecondRound() {
        assertFalse("结束卡上出现了诱导续玩的字眼", PlayVoice.leaveLabel.contains("再来"))
        assertFalse(PlayVoice.doneLine(playThrough(round), 43).contains("再来"))
        assertFalse(PlayVoice.doneNote(round).contains("再来"))
        assertFalse(PlayVoice.doneNote(solvedPair()).contains("再来"))
    }

    /**
     * 第 23 轮加的「换一个游戏」：它往回走，不往前推。
     *
     * 这条路和「再来一局」是相反的两个方向，措辞上不能含糊——说的是**这一个不玩**，
     * 不是**再玩一个**。上面那两张词表已经扫过它，这一条另外钉住它到底说了什么：
     * 万一哪天改成一句听着像邀请的话，而词表恰好没扫到，这里会响。
     */
    @Test
    fun theSwitchLabelBacksOutInsteadOfPushingForward() {
        assertEquals("换一个游戏", PlayVoice.switchLabel)
        for (word in listOf("再来", "继续", "下一", "再玩")) {
            assertFalse("「${PlayVoice.switchLabel}」听着像是让人接着玩", PlayVoice.switchLabel.contains(word))
        }
        assertFalse("结束卡上只有一条退路，不该出现「换一个」", PlayVoice.leaveLabel.contains("换一个"))
    }

    @Test
    fun noLinePushesTheUserToKeepPlaying() {
        val pushWords = listOf("再来", "继续玩", "下一局", "再玩", "倒计时", "还剩", "加油", "坚持")
        val lines = buildList {
            add(PlayVoice.title)
            add(PlayVoice.chooseTitle)
            add(PlayVoice.chooseNote)
            add(PlayVoice.startLabel)
            add(PlayVoice.switchLabel)
            add(PlayVoice.leaveLabel)
            add(PlayVoice.doneNote(round))
            add(PlayVoice.doneNote(solvedPair()))
            add(PlayVoice.headline(round))
            add(PlayVoice.headline(FindPair.deal(Random(1))))
            add(PlayVoice.doneLine(playThrough(round), 43))
            add(PlayVoice.doneLine(solvedPair(), 43))
            add(PlayHint.NONE.text)
            add(PlayHint.ALREADY_DONE.text)
            add(PlayHint.NOT_YET.text)
            add(PlayHint.NOT_THIS_ONE.text)
            for (game in PlayCatalog.games) {
                add(game.name)
                add(game.howTo)
                add(PlayVoice.rule(game))
                add(PlayVoice.promise(game))
            }
        }
        for (line in lines) {
            for (word in pushWords) {
                assertFalse("「$line」里出现催促续玩的「$word」", line.contains(word))
            }
        }
    }

    @Test
    fun noLineComparesThisRoundWithAnythingElse() {
        val compareWords = listOf("上次", "最好", "记录", "排名", "同龄", "比昨天", "进步")
        val lines = buildList {
            add(PlayVoice.chooseNote)
            add(PlayVoice.switchLabel)
            add(PlayVoice.doneNote(round))
            add(PlayVoice.doneNote(solvedPair()))
            add(PlayVoice.doneLine(playThrough(round), 43))
            add(PlayVoice.doneLine(solvedPair(), 43))
            for (game in PlayCatalog.games) add(PlayVoice.promise(game))
        }
        for (line in lines) {
            for (word in compareWords) {
                assertFalse("「$line」里出现跟别人或跟上次比较的「$word」", line.contains(word))
            }
        }
    }

    @Test
    fun theTitleMatchesTheEntryOnTheHomeScreen() {
        // 首页那一行和这一页的表头是同一句话。第 36 轮之前两边各写一遍同一个字面量
        // （GAP-04）；这条钉住的只是**这一页**的写法——改首页那处它**不会**红，原来那句
        // 「改一处漏一处会被扫出来」其实是假的（第 36 轮的反向变异 M2 证过）。现在首页
        // 那一处指向 PlayVoice.title，字面量全项目只剩这里一处，而「两边是不是同一句」
        // 由 HomeVoiceTest 的相等断言盯着。
        assertEquals("玩一会儿", PlayVoice.title)
    }
}
