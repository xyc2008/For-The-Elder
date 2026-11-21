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

    /** 还没开始。先把规矩讲清楚，用户点头了才摆格子。 */
    data class Ready(val game: PlayGame) : PlayUiState

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

    private val mutableState = MutableStateFlow<PlayUiState>(PlayUiState.Ready(PlayCatalog.first))

    val state: StateFlow<PlayUiState> = mutableState.asStateFlow()

    /**
     * 用户点了「开始」：摆一局新的，并把此刻记下来。
     *
     * 洗牌用 [Random.Default]。这里不追求可复现——每周都一样的那才叫怪事；
     * 要复现的是测试，测试自己传 `Random(种子)` 给领域层。
     */
    fun start() {
        val game = PlayCatalog.first
        mutableState.value = PlayUiState.Playing(
            game = game,
            round = game.deal(Random.Default),
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
