package com.fortheelder.ui.play

import com.fortheelder.core.time.AppClock
import com.fortheelder.domain.play.OrderRound
import com.fortheelder.domain.play.PlayCatalog
import com.fortheelder.domain.play.PlayHint
import com.fortheelder.domain.play.SchulteGrid
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime

/**
 * 「玩一会儿」的状态机单元测试（JVM）。
 *
 * 这是第 23 轮第一次给 [PlayViewModel] 写测试。此前这一页的行为只在仪器化测试
 * （`ui/play/PlayScreenTest.kt`）里走过——那跑的是整块屏幕，代价是每个用例几十秒，
 * 所以「只有一个游戏时不摆选择题」这类边角一直没人走过。这一轮把起点判定提成纯函数
 * [playStartState]，它才第一次进得来。
 *
 * 这一层守三件事：
 * 1. 这一页**从哪一步起**只由目录里有几个游戏决定；规则页上「换一个游戏」往回走
 *    不丢任何东西（这一轮加的）；
 * 2. 每个动作**只在它自己那一步**生效：不在那一步就什么都不做，一次误调用不会把
 *    手上那一局悄悄丢掉；
 * 3. 用时只在这一局点完的那一刻取一次时钟（NFR-13），所以它可断言——不必真等上几秒。
 */
class PlayViewModelTest {

    // ---- 这一页从哪一步起（第 23 轮提成纯函数，此前走不到）----

    /**
     * 目录里不止一个游戏：先摆选择题。
     *
     * 顺序也要跟目录一致：几个游戏谁前谁后是目录说了算，不该在别处再排一遍。
     */
    @Test
    fun `目录里不止一个游戏时先摆选择题`() {
        val state = playStartState(PlayCatalog.games)

        val choose = state as? PlayUiState.Choose
        assertTrue("期望 Choose，实际是 $state", choose != null)
        assertEquals(PlayCatalog.games, choose!!.games)
        assertEquals(3, choose.games.size)
    }

    /**
     * 只有一个游戏：直接进它的规则页，而且**不摆**「换一个游戏」。
     *
     * 只有一个还问「你要玩哪个」，就是让人白做一次判断。这条规矩在第 23 轮之前没有任何测试，
     * 它靠的只是 `PlayUiState.Choose` 的 KDoc 里那句承诺——所以这一轮把它钉住。
     */
    @Test
    fun `只有一个游戏时直接进规则页且不给换一个`() {
        val state = playStartState(listOf(SchulteGrid.game))

        val ready = state as? PlayUiState.Ready
        assertTrue("期望 Ready，实际是 $state", ready != null)
        assertSame(SchulteGrid.game, ready!!.game)
        assertFalse("只有一个游戏时不该出现「换一个游戏」", ready.canChooseAgain)
    }

    /**
     * 目录空着也不会把这一页打垮。
     *
     * 生产里到不了这一支（`PlayCatalog.games` 恒有三条），但判据写的是「**恰好**一个」，
     * 不是「空不空」；而起点判定是在 `PlayViewModel` 的属性初始化里跑的——真写成
     * `games.first()` 那种样子，整块屏幕会在构造时炸掉，用户看到的是空白。
     */
    @Test
    fun `目录为空的边界不会崩`() {
        val state = playStartState(emptyList())

        val choose = state as? PlayUiState.Choose
        assertTrue("期望 Choose，实际是 $state", choose != null)
        assertTrue(choose!!.games.isEmpty())
    }

    // ---- 挑一个游戏 → 规则页 ----

    /** 挑完先进规则页，不直接开局：规矩要先讲清楚。 */
    @Test
    fun `挑了游戏先进规则页而不是开局`() {
        val viewModel = viewModel()

        viewModel.choose(SchulteGrid.game)

        val ready = viewModel.state.value as? PlayUiState.Ready
        assertTrue("期望 Ready，实际是 ${viewModel.state.value}", ready != null)
        assertSame(SchulteGrid.game, ready!!.game)
        assertTrue("能挑就说明目录里不止一个游戏，所以这里可以换一个", ready.canChooseAgain)
    }

    /** 不是选择页时挑不动：挑这个动作认识的是选择页那张屏。 */
    @Test
    fun `不在选择页时挑不动`() {
        val viewModel = viewModel()
        viewModel.choose(SchulteGrid.game)
        val before = viewModel.state.value

        viewModel.choose(PlayCatalog.games[1])

        assertEquals(before, viewModel.state.value)
    }

    // ---- 规则页上的第二条退路（第 23 轮加的）----

    /** 规则页点「换一个游戏」回到选择页，而且目录照旧全摆出来。 */
    @Test
    fun `换一个游戏从规则页回到选择页`() {
        val viewModel = viewModel()
        viewModel.choose(SchulteGrid.game)

        viewModel.chooseAgain()

        val choose = viewModel.state.value as? PlayUiState.Choose
        assertTrue("期望 Choose，实际是 ${viewModel.state.value}", choose != null)
        assertEquals(PlayCatalog.games, choose!!.games)
    }

    /**
     * 还没挑过就点「换一个游戏」，仍然停在同一处。
     *
     * 按钮在规则页之外不出现，所以用户点不到这里；这条守的是「往回走」不改变起点——
     * 选择页还是那张选择页，不会凭空变成规则页。
     */
    @Test
    fun `还没挑过时换一个游戏仍是选择页`() {
        val viewModel = viewModel()

        viewModel.chooseAgain()

        val choose = viewModel.state.value as? PlayUiState.Choose
        assertTrue("期望 Choose，实际是 ${viewModel.state.value}", choose != null)
    }

    /**
     * 正在玩的时候「往回走」这一下什么都不做。
     *
     * 规则页之外不出现那个按钮，所以这条只有误调用才碰得到；但它守的是这一刻**手上那一局**：
     * 正在点的格子不该因为别处一下点击就没了。
     */
    @Test
    fun `正在玩的时候换一个游戏不动这一局`() {
        val viewModel = viewModel()
        viewModel.choose(SchulteGrid.game)
        viewModel.start()
        val before = viewModel.state.value

        viewModel.chooseAgain()

        assertEquals(before, viewModel.state.value)
    }

    // ---- 规则页 → 一局 ----

    /** 点了开始就摆出**挑中的那一个**：摆哪个是状态说的，不是开局时再查一次目录。 */
    @Test
    fun `开始就摆出挑中的那一个`() {
        val findPair = PlayCatalog.games[1]
        val viewModel = viewModel()
        viewModel.choose(findPair)

        viewModel.start()

        val playing = viewModel.state.value as? PlayUiState.Playing
        assertTrue("期望 Playing，实际是 ${viewModel.state.value}", playing != null)
        assertSame(findPair, playing!!.game)
        assertEquals("找一样是 4×4", 16, playing.round.cells.size)
        assertEquals(2, playing.round.stepsToFinish)
    }

    /** 摆出来的一局是该游戏自己出的，不是别的游戏串了台。 */
    @Test
    fun `开始摆的是舒尔特的一局`() {
        val viewModel = viewModel()
        viewModel.choose(SchulteGrid.game)

        viewModel.start()

        val playing = viewModel.state.value as PlayUiState.Playing
        val round = playing.round
        assertTrue("舒尔特的一局是 OrderRound，实际是 ${round::class.simpleName}", round is OrderRound)
        assertEquals(SchulteGrid.ID, round.gameId)
        assertEquals(25, round.cells.size)
        assertEquals(25, round.stepsToFinish)
    }

    /** 不在规则页时点开始不动：跟挑一个、往回走同一套写法。 */
    @Test
    fun `不在规则页时点开始不动`() {
        val viewModel = viewModel()
        val before = viewModel.state.value

        viewModel.start()

        assertEquals(before, viewModel.state.value)
    }

    // ---- 点格子 ----

    /**
     * 点错一格：提示换了，这一局别的什么都没动。
     *
     * 「别的都没动」比的是**去掉提示之后**的那一局：提示本来就该换，而进度、盘面、
     * 开始时刻一个都不许动——也没有任何字段能记下这一次点错（AC-12.3）。
     */
    @Test
    fun `点错一格只换一句话`() {
        val viewModel = viewModel()
        viewModel.choose(SchulteGrid.game)
        viewModel.start()
        val playing = viewModel.state.value as PlayUiState.Playing
        val round = playing.round as OrderRound
        val wrong = round.cells.indexOfFirst { it != round.target }

        viewModel.tap(wrong)

        val after = viewModel.state.value as PlayUiState.Playing
        val afterRound = after.round as OrderRound
        assertEquals("点错不该动这一局的进度", round, afterRound.copy(hint = PlayHint.NONE))
        assertEquals(PlayHint.NOT_YET, afterRound.hint)
        assertEquals("开始那一刻不变", playing.startedAt, after.startedAt)
    }

    /** 按顺序点完 25 下，这一局就完了；用时由时钟算出来。 */
    @Test
    fun `按顺序点完就完了且用时来自时钟`() {
        val start = LocalDateTime.of(2026, 11, 21, 9, 0, 0)
        clock = FakeClock(start)
        val viewModel = viewModel()
        viewModel.choose(SchulteGrid.game)
        viewModel.start()
        val round = (viewModel.state.value as PlayUiState.Playing).round as OrderRound

        // 一局用到的时间由假时钟给：这里只让它往前走 41 秒，不用真等
        clock.value = start.plusSeconds(41)
        for (number in round.order) {
            viewModel.tap(round.cells.indexOf(number))
        }

        val finished = viewModel.state.value as? PlayUiState.Finished
        assertTrue("期望 Finished，实际是 ${viewModel.state.value}", finished != null)
        assertEquals(41, finished!!.seconds)
        assertTrue(finished.round.finished)
    }

    /** 用户把系统时间往回调了：用时按 0 算，不显示一个负的秒数。 */
    @Test
    fun `时钟往回调时用时按 0 算`() {
        val start = LocalDateTime.of(2026, 11, 21, 9, 0, 0)
        clock = FakeClock(start)
        val viewModel = viewModel()
        viewModel.choose(SchulteGrid.game)
        viewModel.start()
        val round = (viewModel.state.value as PlayUiState.Playing).round as OrderRound

        clock.value = start.minusSeconds(30)
        for (number in round.order) {
            viewModel.tap(round.cells.indexOf(number))
        }

        assertEquals(0, (viewModel.state.value as PlayUiState.Finished).seconds)
    }

    /** 点完之后再点不会把这一局改成别的样子。 */
    @Test
    fun `点完之后再点不动`() {
        val viewModel = viewModel()
        viewModel.choose(SchulteGrid.game)
        viewModel.start()
        val round = (viewModel.state.value as PlayUiState.Playing).round as OrderRound
        for (number in round.order) {
            viewModel.tap(round.cells.indexOf(number))
        }
        val finished = viewModel.state.value

        viewModel.tap(0)

        assertEquals(finished, viewModel.state.value)
    }

    /** 不在玩的时候点格子不动：格子只在玩的那一屏上。 */
    @Test
    fun `不在玩的时候点格子不动`() {
        val viewModel = viewModel()
        viewModel.choose(SchulteGrid.game)
        val before = viewModel.state.value

        viewModel.tap(0)

        assertEquals(before, viewModel.state.value)
    }

    // ---- 测试脚手架 ----

    /** 一个可以拨的时钟：拨到哪儿就是哪儿，不用真等。 */
    private var clock = FakeClock(LocalDateTime.of(2026, 11, 21, 9, 0, 0))

    private fun viewModel() = PlayViewModel(clock)

    /**
     * [AppClock] 是个 `fun interface`，所以这里不必写一个类去实现它之外的东西——
     * 生产上用的是 `SystemAppClock`，测试里用的是 [value]。
     */
    private class FakeClock(var value: LocalDateTime) : AppClock {
        override fun now(): LocalDateTime = value
    }
}
