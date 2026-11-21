package com.fortheelder.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fortheelder.core.time.AppClock
import com.fortheelder.data.intervention.InterventionStore
import com.fortheelder.data.prefs.SettingsStore
import com.fortheelder.data.rhythm.RhythmProfileService
import com.fortheelder.domain.engine.greeting.GreetingEngine
import com.fortheelder.domain.engine.intervention.InterventionConfig
import com.fortheelder.domain.engine.intervention.InterventionVerdict
import com.fortheelder.domain.engine.intervention.InterventionWindow
import com.fortheelder.domain.engine.rhythm.RhythmResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** 首页要显示的东西。 */
sealed interface HomeUiState {
    /** 还没读完设置、还没判定完这次要不要说话。只有最初一瞬间会处于这个状态。 */
    data object Loading : HomeUiState

    /** 这次迎上来说的话（[Opening] 可能是问候，也可能是一句安静语）。 */
    data class Ready(
        val opening: Opening,
        /** 用户还没填称呼，此时首页会出现一个补充入口。 */
        val needsAddress: Boolean,
    ) : HomeUiState
}

/**
 * 首页状态。
 *
 * 这里有两件独立的事，别混在一起看：
 *
 * 1. **问候语每分钟重读一次时间**（[refreshTick]）。问候语是时间与设置的函数，而 Flow 只能
 *    感知设置的变化：跨过早上七点、中午十二点这些时段边界时设置没有任何改动，界面就会一直
 *    停在上一时段的问候语上。所以由界面按分钟触发一次，把「现在」重新读一遍。
 *    这里没有用「算到下一个时段边界还差多少毫秒」的做法：那种写法在系统时间被改动、
 *    设备从待机唤醒时容易算错，多读几次时间的成本可以忽略。
 *
 * 2. **这次要不要迎上来说话，一次露面只判一次**（[session]）。判定依据是作息画像和节流状态，
 *    两者都是**开机那一刻的快照**，之后本分钟内不再重读——这一点很关键：判定之后我们会把
 *    「已经招呼过」记进 [InterventionStore]，要是随后又去重读状态，就会立刻看到自己刚写进去的
 *    记录而改口成「先不重复招呼了」，等于自己把自己说没了。同一个露面里短暂切出去再回来不算
 *    新的露面，所以也不会改口；真的退出去重进、或者从后台回来，则重新判一次
 *    （[onStart]），免得一直挂着十几分钟前的结论。
 */
class HomeViewModel(
    settings: SettingsStore,
    private val greetingEngine: GreetingEngine,
    private val clock: AppClock,
    private val interventions: InterventionStore,
    private val profileService: RhythmProfileService,
    private val config: InterventionConfig = InterventionConfig(),
) : ViewModel() {

    private val refreshTick = MutableStateFlow(0L)

    /** 本次露面的判定结果。null 表示还在算。 */
    private val session = MutableStateFlow<SessionFacts?>(null)

    /** 两类判定依据的快照，合在一起只为了往一个 Flow 里塞。 */
    private class SessionFacts(val rhythmResult: RhythmResult, val verdict: InterventionVerdict)

    /**
     * 串行化「开一次露面」：`init` 和界面回报的「又回来了」有可能挨得很近，
     * 两次并发判定会各自读到同一份旧状态，把一次招呼记成两次。
     */
    private val openingMutex = Mutex()

    /** 上一次露面之后，界面真的离开过前台吗。判 ON_START 是否算「新的一次露面」。 */
    private var leftSinceLastOpening = false

    init {
        open()
    }

    val state: StateFlow<HomeUiState> = combine(
        settings.settings,
        refreshTick,
        session,
    ) { appSettings, _, facts ->
        val current = facts ?: return@combine HomeUiState.Loading
        HomeUiState.Ready(
            opening = InterventionVoice.opening(
                verdict = current.verdict,
                greeting = greetingEngine.greet(
                    address = appSettings.address,
                    firstLaunchEpochDay = appSettings.firstLaunchEpochDay,
                ),
            ),
            needsAddress = appSettings.address.isNullOrBlank(),
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
        initialValue = HomeUiState.Loading,
    )

    /**
     * 用户又来看这一页了（从后台回来）。重新判一次。
     *
     * 不重新判的话，用户十分钟前看到的那句话会一直挂着：安静语可能早已过期
     * （「先不重复招呼了」在半小时后就不成立了），问候语也会停在旧时段。
     *
     * 但**必须与 [onStop] 配对**才认这一次露面。原因是 `LifecycleRegistry.addObserver` 会把
     * 新观察者同步推进到当前状态：界面刚订阅时，系统会依次补发 ON_CREATE / ON_START / ON_RESUME。
     * 那一刻这个 ViewModel 刚在 `init` 里判过一次，要是看到 ON_START 就再判，冷启动会连判两次——
     * 第二次读到的是刚刚写下的「已经招呼过」，首页于是从问候翻成「先不重复招呼了」。
     * 只有真的先离开过（[onStop]），回来时的 ON_START 才算新的一次露面。
     */
    fun onStart() {
        if (!leftSinceLastOpening) return
        leftSinceLastOpening = false
        open()
    }

    /**
     * 界面离开前台。只做个记号，不取消任何东西：判定完要落盘，
     * 半路掐掉会留下「说过话但没记账」的状态，下次露面就会重复招呼一遍。
     */
    fun onStop() {
        leftSinceLastOpening = true
    }

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

    /**
     * 走一次「露面」：读作息、读节流状态、判定、必要时记账。
     *
     * 只有判定为要说话时才记 [InterventionStore.recordSpoken]：安静的时候不该计入
     * 「已经招呼过」，否则用户在深夜打开一次应用，第二天早上就会被当成「不理人」。
     */
    private fun open() {
        viewModelScope.launch {
            openingMutex.withLock {
                val rhythmResult = profileService.current()
                val throttle = interventions.stateOnce()
                val verdict = InterventionWindow.decide(rhythmResult, clock.now(), throttle, config)
                if (verdict is InterventionVerdict.Speak) {
                    interventions.recordSpoken()
                }
                session.value = SessionFacts(rhythmResult, verdict)
            }
        }
    }

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
