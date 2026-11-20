package com.fortheelder.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fortheelder.core.time.AppClock
import com.fortheelder.data.prefs.SettingsStore
import com.fortheelder.domain.engine.greeting.Greeting
import com.fortheelder.domain.engine.greeting.GreetingEngine
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/** 首页要显示的东西。 */
sealed interface HomeUiState {
    /** 设置还没从磁盘读出来。只有首帧会短暂处于这个状态。 */
    data object Loading : HomeUiState

    data class Ready(
        val greeting: Greeting,
        /** 用户还没填称呼，此时首页会出现一个补充入口。 */
        val needsAddress: Boolean,
    ) : HomeUiState
}

/**
 * 首页状态。
 *
 * 问候语是**时间**和**设置**两件事的函数，而 Flow 只能感知设置的变化：跨过早上七点、
 * 中午十二点这些时段边界时，设置没有任何改动，界面就会一直停留在上一个时段的问候语。
 * 所以额外引入 [refreshTick] 作为第二个上游，由界面按分钟触发一次，把「现在」重新读一遍。
 *
 * 这里没有用「算到下一个时段边界还差多少毫秒」的做法：那种写法在系统时间被改动、
 * 或者设备从待机唤醒时容易算错，多读几次时间的成本可以忽略。
 */
class HomeViewModel(
    settings: SettingsStore,
    private val greetingEngine: GreetingEngine,
    private val clock: AppClock,
) : ViewModel() {

    private val refreshTick = MutableStateFlow(0L)

    val state: StateFlow<HomeUiState> = combine(settings.settings, refreshTick) { appSettings, _ ->
        appSettings
    }.map { appSettings ->
        HomeUiState.Ready(
            greeting = greetingEngine.greet(
                address = appSettings.address,
                firstLaunchEpochDay = appSettings.firstLaunchEpochDay,
            ),
            needsAddress = appSettings.address.isNullOrBlank(),
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
        initialValue = HomeUiState.Loading,
    )

    /**
     * 让界面重新读一次时钟。
     *
     * 必须真的碰一下 [clock]：否则编译器会把「clock 只在构造时用了一次」视为冗余参数。
     * 更重要的是语义——这个类对外承诺的是「刷新时以当前时间为准」，取值动作要写出来。
     */
    fun refresh() {
        clock.now()
        refreshTick.value = refreshTick.value + 1
    }

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
