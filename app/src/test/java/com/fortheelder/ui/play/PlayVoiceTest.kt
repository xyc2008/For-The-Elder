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
    fun thePromiseTakesTheCeilingFromTheGameItself() {
        // 「一局最多几分钟」是算出来的，不是文案里手写死的数字：
        // 游戏把上限改成 120 秒，这句话会跟着变成 2 分钟。
        assertEquals("一局最多 3 分钟。不用计时，也没有分数跟谁比。", PlayVoice.promise(SchulteGrid.game))
        assertEquals("一局最多 2 分钟。不用计时，也没有分数跟谁比。", PlayVoice.promise(FindPair.game))
        assertEquals(180, game.longestSeconds)
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

    @Test
    fun noLinePushesTheUserToKeepPlaying() {
        val pushWords = listOf("再来", "继续玩", "下一局", "再玩", "倒计时", "还剩", "加油", "坚持")
        val lines = buildList {
            add(PlayVoice.title)
            add(PlayVoice.chooseTitle)
            add(PlayVoice.chooseNote)
            add(PlayVoice.startLabel)
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
        // 首页那一行和这一页的表头是同一句话。两处各写一遍是这个版本里已知的重复
        // （GAP-04 文案集中化排在第 16 轮），所以在这里钉一条，改一处漏一处会被扫出来。
        assertEquals("玩一会儿", PlayVoice.title)
    }
}
