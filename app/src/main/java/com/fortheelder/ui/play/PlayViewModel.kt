package com.fortheelder.ui.play

import androidx.lifecycle.ViewModel
import com.fortheelder.core.time.AppClock
import com.fortheelder.domain.play.PlayCatalog
import com.fortheelder.domain.play.PlayGame
import com.fortheelder.domain.play.PlayRound
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.Duration
import java.time.LocalDateTime
import kotlin.random.Random

/** 这一页此刻在哪一步。 */
sealed interface PlayUiState {

    /**
     * 选择页：先挑一个游戏。
     *
     * 只有目录里**不止一个**游戏时才出现。只有一个游戏还问「你要玩哪个」，
     * 就是让人白做一次判断——那时入口仍然直接进 [Ready]。
     */
    data class Choose(val games: List<PlayGame>) : PlayUiState

    /**
     * 还没开始。先把规矩讲清楚，用户点头了才摆格子。
     *
     * [canChooseAgain] 是第 23 轮加的：目录里还有别的游戏时，这一页底下多一条「换一个游戏」。
     * 把它放进状态而不是让界面去问 `PlayCatalog`，是为了让「界面只管照着状态画」这条自律
     * 在这里也成立——否则每个分支都要单独去问一次「现在有几个游戏」。
     * 只有一个游戏那一支里它是 `false`，那正是 [Choose] 说的同一种情况：无处可换，就别摆那个按钮。
     */
    data class Ready(val game: PlayGame, val canChooseAgain: Boolean) : PlayUiState

    /**
     * 正在玩。
     *
     * [startedAt] 是这一局开始的时刻，**只用来算这一局的用时**。它在内存里，
     * 记下来的是个 `LocalDateTime` 而不是别的——这一局没有编号，所以它不可能被当成
     * 「第几局」存到哪儿去。
     */
    data class Playing(val game: PlayGame, val round: PlayRound, val startedAt: LocalDateTime) : PlayUiState

    /**
     * 这一局点完了。
     *
     * 连 [round] 一起留着，是为了能说出「25 个都点到了」这句话——这不是成绩，
     * 是刚刚发生的事；说完这一屏就结束了。
     */
    data class Finished(val game: PlayGame, val round: PlayRound, val seconds: Int) : PlayUiState
}

/**
 * 「玩一会儿」的状态。
 *
 * 这个类里**没有 viewModelScope、没有任何 repository、没有一处写入**——不是省事，
 * 是因为这一页真的什么都不存（AC-12.2、DR-09、AC-12.4）。状态全在 [mutableState] 里：
 * 转屏时它跟着 `ViewModel` 活下来，用户从中途退出、或者离开这一页，它就被丢掉，
 * 库里不会多出一条「没玩完」的记录。
 *
 * 计时走 [AppClock]（NFR-13），所以「用时」这句话在单元测试里是可断言的，
 * 不需要真等上几秒。
 */
class PlayViewModel(private val clock: AppClock) : ViewModel() {

    private val mutableState = MutableStateFlow<PlayUiState>(chooseOrReady())

    val state: StateFlow<PlayUiState> = mutableState.asStateFlow()

    /**
     * 目录里有几个游戏，决定了这一页从哪一步起。
     *
     * 起点（上面那个属性）与 [chooseAgain] 共用这一处判断：两处各写一遍的话，
     * 只有一个游戏时会退回一个只摆着一张卡的选择页——那正是 [PlayUiState.Choose] 说不要发生的事。
     * 判定本身是 [playStartState] 这条纯函数，所以它在单元测试里走得到（第 23 轮之前走不到）。
     */
    private fun chooseOrReady(): PlayUiState = playStartState(PlayCatalog.games)

    /**
     * 用户在选择页挑了一个游戏。挑完先去它的规则页，不直接开局。
     *
     * 只在选择页那张屏上生效（跟 [start]、[tap] 同一套写法）：从规则页、或者从正在玩的一局
     * 再挑一次，是**换掉手上这一局**，那不是这个动作的意思。挡住之后调用方不必自己记得这件事。
     */
    fun choose(game: PlayGame) {
        if (mutableState.value !is PlayUiState.Choose) return
        // 能挑就说明目录里不止一个游戏：选择页本身只在那种时候才存在
        // （见 [PlayUiState.Choose]），所以这里不必再去问一次目录。
        mutableState.value = PlayUiState.Ready(game, canChooseAgain = true)
    }

    /**
     * 规则页上点了「换一个游戏」：回到选择页。
     *
     * 只在规则页用得上：这一刻这一局**还没摆出来**（[start] 才摆），所以退回去不丢任何东西——
     * 这也正是这条路只开在规则页、不跟在玩的一局旁边的理由（见 `PlayScreen` 里 `ReadyPanel`
     * 的说明）。所以这里也像 [start]、[tap] 那样挡一道：不在规则页就什么都不做，正在玩的那一局
     * 不会被这一下悄悄丢掉。只有一个游戏时它是空操作，而那种情况下 `Ready.canChooseAgain`
     * 是 `false`、按钮压根不出现，所以用户点不到这里。
     */
    fun chooseAgain() {
        if (mutableState.value !is PlayUiState.Ready) return
        mutableState.value = chooseOrReady()
    }

    /**
     * 用户点了「开始」：摆一局新的，并把此刻记下来。
     *
     * 摆哪个游戏是**状态说的**，不是这里再查一次目录：选择页挑的是哪一个，
     * 这里就摆哪一个，两边不会各说各的。
     *
     * 洗牌用 [Random.Default]。这里不追求可复现——每周都一样的那才叫怪事；
     * 要复现的是测试，测试自己传 `Random(种子)` 给领域层。
     */
    fun start() {
        val ready = mutableState.value as? PlayUiState.Ready ?: return
        mutableState.value = PlayUiState.Playing(
            game = ready.game,
            round = ready.game.deal(Random.Default),
            startedAt = clock.now(),
        )
    }

    /**
     * 用户点了第 [index] 格。
     *
     * 判对错、给提示全部是 [PlayRound.tap] 这一条纯函数的事，界面只负责把新的一局放回去。
     * 点错时返回的一局和原来只差一个提示，**没有任何计数被加**，界面想做点什么也做不到。
     *
     * 只有点完最后一下才去取时钟：这一步需要「现在几点」，而前面每一步都不需要。
     */
    fun tap(index: Int) {
        val playing = mutableState.value as? PlayUiState.Playing ?: return
        val next = playing.round.tap(index)
        mutableState.value = if (next.finished) {
            PlayUiState.Finished(playing.game, next, secondsSince(playing.startedAt))
        } else {
            playing.copy(round = next)
        }
    }

    /** 这一局已经过了几秒。负值（用户把系统时间往回调了）按 0 算，不显示一个负的秒数。 */
    private fun secondsSince(start: LocalDateTime): Int =
        Duration.between(start, clock.now()).seconds.coerceAtLeast(0L).toInt()
}

/**
 * 这一页从哪一步起，只由「目录里有几个游戏」决定。
 *
 * 写成收 [games] 的纯函数、而不是直接读 [PlayCatalog]，是为了让「只有一个游戏时不摆选择题」
 * 这条规则第一次能被单元测试走一遍——第 23 轮之前它没有任何测试。
 */
internal fun playStartState(games: List<PlayGame>): PlayUiState =
    if (games.size == 1) {
        PlayUiState.Ready(games.first(), canChooseAgain = false)
    } else {
        PlayUiState.Choose(games)
    }
