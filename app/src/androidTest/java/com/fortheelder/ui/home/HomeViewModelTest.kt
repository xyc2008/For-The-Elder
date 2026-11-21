package com.fortheelder.ui.home

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.fortheelder.core.time.AppClock
import com.fortheelder.data.anchor.AnchorRepository
import com.fortheelder.data.db.ElderDatabase
import com.fortheelder.data.intervention.InterventionStore
import com.fortheelder.data.prefs.SettingsStore
import com.fortheelder.data.rhythm.RhythmProfileService
import com.fortheelder.data.rhythm.RhythmRepository
import com.fortheelder.domain.companion.AnchorRules
import com.fortheelder.domain.companion.AnchorSlot
import com.fortheelder.domain.companion.CompanionVoice
import com.fortheelder.domain.engine.greeting.GreetingEngine
import com.fortheelder.domain.engine.intervention.InterventionConfig
import com.fortheelder.domain.engine.rhythm.DailyRhythm
import java.io.File
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 首页「露面」这条链路上，那几条只有接上真数据库与真 DataStore 才成立的不变量。
 *
 * 这里验的**不是界面长得对不对**（那是 `HomeScreenTest` 的活），而是几次露面之间
 * 状态怎么走：判定、记账、摆锚点、重读问候语，各自在什么时候发生。
 * - AC-05.1「安静时段开口就说休息，不迎上来」；
 * - AC-05.2「刚说过话就不再重复招呼」；
 * - AC-13.1「改称呼之后首页立刻改口」；
 * - AC-03.1「时段划分覆盖全天」（跨边界靠 [HomeViewModel.refresh] 重读）；
 * - AC-04.4「没有画像时退化为按普通作息，且明说」。
 *
 * 用的是 App 自己在用的那几个仓库与 store，不是替身：这些用例的断言大半是
 * 「库里到底写没写」，替身只能证明调用发生。所以这个类只能跑在设备上——
 * [HomeViewModel] 的构造参数都是要 Room / DataStore 的具体类，JVM 侧建不出来。
 *
 * ## 为什么不能只 `state.first { it is Ready }`
 *
 * [HomeViewModel.state] 是 `stateIn(..., SharingStarted.WhileSubscribed(5_000), Loading)`。
 * 只在取值时订阅一下，拿到值之后订阅立刻结束，**之后任何重算都不会发生**——
 * `refresh()`、`onStart()`、DataStore 写入引起的重新组合全都白做。那样「不该变」的用例
 * 会变成恒真（假绿），「该变」的用例则会一直超时。
 *
 * 所以 [openHome] 里挂了一个常驻收集器，把 `state` 镜像到自己一条热流 [mirror] 上，
 * 断言一律读 [mirror]。
 *
 * 两个约定，写在前面免得后来人误会用例在等什么：
 * 1. **变了的事**用 [awaitUntil] 等出结果，DataStore 落盘与 Flow 重新发射都隔着一次异步派发；
 * 2. **不该变的事**没有可等的信号，只能用 [settleQuietly] 给一段固定的等待窗口。
 */
@RunWith(AndroidJUnit4::class)
class HomeViewModelTest {

    private lateinit var context: Context
    /** 自己的库文件，不碰应用那个 `elder.db`；原因见 `ElderDatabase.open` 的注释。 */
    private val testDb = "elder-test-home-vm.db"

    private lateinit var database: ElderDatabase
    private lateinit var anchorRepository: AnchorRepository
    private lateinit var rhythmRepository: RhythmRepository
    private lateinit var profileService: RhythmProfileService
    private lateinit var settingsStore: SettingsStore
    private lateinit var interventionStore: InterventionStore
    private lateinit var greetingEngine: GreetingEngine

    private lateinit var dataStoreFile: File
    private lateinit var storeScope: CoroutineScope
    private lateinit var dataStore: DataStore<Preferences>

    /** 固定时区与固定「现在」：不用系统默认值，免得跑测试的机器换个时区结果就变。 */
    private lateinit var clock: AppClock
    private val zone: ZoneId = ZoneId.of("Asia/Shanghai")
    private var now: LocalDateTime = LocalDateTime.of(2026, 3, 10, 9, 0)

    /** 判定参数取默认值：用例要验的就是发布版的那套间隔（30 分钟起、随忽略递增）。 */
    private val config: InterventionConfig = InterventionConfig()

    /** 一个目录里真的有的起点，用来做锚点。 */
    private val paper: String = "calligraphy-paper"

    /** 第二个起点：一个起点只记一条日常，要摆出第二条锚点就得换一件事。 */
    private val pen: String = "calligraphy-pen"

    /** 视图模型装在真的 store 里，`@After` 才能走到 `onCleared`，两条 collector 才收得掉。 */
    private lateinit var viewModelStore: ViewModelStore
    private lateinit var viewModel: HomeViewModel

    /** 常驻订阅 `viewModel.state` 的那条协程；它自己不产生断言，只为让 `state` 一直活着。 */
    private lateinit var observerScope: CoroutineScope
    private lateinit var observerJob: Job

    /** [viewModel.state] 的镜像。断言读它，不直接读 `state`（理由见类注释）。 */
    private lateinit var mirror: MutableStateFlow<HomeUiState>

    @Before
    fun setUp() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        context.deleteDatabase(testDb)
        database = ElderDatabase.open(context, testDb)

        clock = AppClock { now }
        anchorRepository = AnchorRepository(database, clock, zone)
        rhythmRepository = RhythmRepository(database, clock, zone)
        profileService = RhythmProfileService(rhythmRepository, clock)
        greetingEngine = GreetingEngine(clock)

        // 真的 DataStore 文件而不是内存假件：`recordSpoken` 的落盘本身也在被测范围内。
        // 文件名每次唯一，因为同一个文件上不能同时有两个 DataStore 活着。
        dataStoreFile = File(context.cacheDir, "home-vm-${System.nanoTime()}.preferences_pb")
        storeScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        dataStore = PreferenceDataStoreFactory.create(scope = storeScope) { dataStoreFile }
        interventionStore = InterventionStore(dataStore, clock, zone)
        settingsStore = SettingsStore(dataStore, clock)
    }

    @After
    fun tearDown() {
        // 先收摊再关库：镜像收集器还挂在 Room / DataStore 的 Flow 上时关库，异常会打在
        // 没人接的协程里，变成一条跟被测代码无关的崩溃。
        if (::observerJob.isInitialized) observerJob.cancel()
        if (::observerScope.isInitialized) observerScope.cancel()
        if (::viewModelStore.isInitialized) viewModelStore.clear()
        storeScope.cancel()
        database.close()
        context.deleteDatabase(testDb)
        dataStoreFile.delete()
    }

    // ---- AC-05.1：安静时段不迎上来，也不记账 ----

    @Test
    fun `安静时段不记账也不摆锚点`() = runBlocking(Dispatchers.IO) {
        // 先在「想起来的时候」记下一件小事：这个时段覆盖 23:00。
        // 有这样一条在库里，下面那句「没摆出来」才是真的差异，而不是「本来就没有」。
        assertTrue(anchorRepository.add(AnchorRules.draftOf(paper, AnchorSlot.ANYTIME)!!))

        now = LocalDateTime.of(2026, 3, 10, 23, 0)
        openHome()

        val ready = awaitReady()
        assertEquals("这会儿是您休息的时间。", ready.opening.headline)
        assertEquals("我就不迎上来说话了，安静陪着您。您要是想说什么，随时说。", ready.opening.note)
        assertNull("判定为安静时不该把用户记下的事摆出来", ready.opening.anchorLine)
        // 安静不算「已经招呼过」：否则深夜开一次应用，第二天早上就会被当成不理人。
        assertNull(interventionStore.stateOnce().lastSpokenAt)
    }

    // ---- AC-05 的正面：说话的时候真的记账 ----

    @Test
    fun `说话时真的记账，库里出现上次开口`() = runBlocking(Dispatchers.IO) {
        openHome()

        val ready = awaitReady()
        assertEquals("上午好", ready.opening.headline)
        // 没有画像（信号不够）→ 问候原句 + 明说按什么作息，AC-04.4。
        assertEquals("第一次见面，慢慢来。说话的时机，我先按普通的作息来。", ready.opening.note)
        assertNull("库里没有锚点，就不该有这一行", ready.opening.anchorLine)
        // 全新一份设置：还没填称呼，首页该给出补充入口（AC-13.1）。
        assertTrue("还没填称呼，首页该有补充入口", ready.needsAddress)

        // 记账落在库里，不是只活在内存里。
        assertEquals(now, interventionStore.stateOnce().lastSpokenAt)
    }

    // ---- 锚点只有命中时段才摆出来，且只在真的说话时读 ----

    @Test
    fun `只有命中时段的锚点才带出那一行`() = runBlocking(Dispatchers.IO) {
        // 09:00 不在「晚上」里，这条不该冒出来。
        assertTrue(anchorRepository.add(AnchorRules.draftOf(paper, AnchorSlot.EVENING)!!))
        openHome()

        val first = awaitReady()
        assertEquals("上午好", first.opening.headline)
        assertNull("没命中时段的锚点不该摆出来", first.opening.anchorLine)

        // 换一次露面（真的走开过），这次库里多了一条命中 09:45 的锚点。
        // 换一件事来记：唯一索引落在 optionId 上，一个起点只记一条日常，同一件事改时段要先删。
        val morning = AnchorRules.draftOf(pen, AnchorSlot.MORNING)!!
        assertTrue(anchorRepository.add(morning))
        now = LocalDateTime.of(2026, 3, 10, 9, 45)
        viewModel.onStop()
        viewModel.onStart()

        awaitUntil { (mirror.value as? HomeUiState.Ready)?.opening?.anchorLine != null }
        val second = awaitReady()
        val stored = anchorRepository.anchors.first().first { it.slot == AnchorSlot.MORNING }
        // 摆出来的那一行就是库里的那一条，不是另拼一份。
        assertEquals(CompanionVoice.dueLine(stored), second.opening.anchorLine)
        // 换了露面才重读：上一段露面里它不在这儿。
        assertTrue(second.opening.anchorLine!!.contains(morning.action))
    }

    // ---- 核心：一次露面只判一次 ----

    @Test
    fun `同一露面内再刷新也不改口`() = runBlocking(Dispatchers.IO) {
        openHome()
        val before = awaitReady()
        assertEquals("第一次见面，慢慢来。说话的时机，我先按普通的作息来。", before.opening.note)

        viewModel.refresh()
        settleQuietly()

        // 刷新只重读「现在几点」，不重判：重判会读到自己刚写下的记账，
        // 间隔 0 分钟 < 30，于是当场从问候翻成「先不重复招呼了」。
        val after = mirror.value
        assertTrue("期望 Ready，实际是 $after", after is HomeUiState.Ready)
        assertEquals(before.opening, (after as HomeUiState.Ready).opening)
        assertEquals(now, interventionStore.stateOnce().lastSpokenAt)
    }

    @Test
    fun `没真的离开过就不算新露面`() = runBlocking(Dispatchers.IO) {
        openHome()
        val before = awaitReady()

        // 只报「又回到前台」、没先报「离开过」：冷启动时系统会补发一次 ON_START，
        // 要是见到就重判，首页会在支起的一瞬间自己改口。
        now = LocalDateTime.of(2026, 3, 10, 9, 5)
        viewModel.onStart()
        settleQuietly()

        val after = mirror.value
        assertTrue("期望 Ready，实际是 $after", after is HomeUiState.Ready)
        assertEquals(before.opening, (after as HomeUiState.Ready).opening)
        assertEquals(LocalDateTime.of(2026, 3, 10, 9, 0), interventionStore.stateOnce().lastSpokenAt)
    }

    @Test
    fun `离开过再回来才重判：太近不重复招呼，够久了再说`() = runBlocking(Dispatchers.IO) {
        openHome()
        assertEquals("上午好", awaitReady().opening.headline)

        // 真的走开过又回来，十分钟后：间隔不够，换成安静语（AC-05.2）。
        now = LocalDateTime.of(2026, 3, 10, 9, 10)
        viewModel.onStop()
        viewModel.onStart()
        awaitUntil { (mirror.value as? HomeUiState.Ready)?.opening?.headline == "先不重复招呼了。" }
        assertEquals("我在这儿。想说话随时说。", (mirror.value as HomeUiState.Ready).opening.note)
        assertEquals(LocalDateTime.of(2026, 3, 10, 9, 0), interventionStore.stateOnce().lastSpokenAt)

        // 又走开过，这回间隔 45 分钟够了：重新迎上来说话，并重新记账。
        now = LocalDateTime.of(2026, 3, 10, 9, 45)
        viewModel.onStop()
        viewModel.onStart()
        awaitUntil { (mirror.value as? HomeUiState.Ready)?.opening?.headline == "上午好" }
        assertEquals(now, interventionStore.stateOnce().lastSpokenAt)
    }

    // ---- AC-13.1：改称呼之后首页立刻改口 ----

    @Test
    fun `称呼填上之后首页立刻改口`() = runBlocking(Dispatchers.IO) {
        openHome()
        val before = awaitReady()
        assertTrue("还没填称呼，应该有个补充入口", before.needsAddress)
        assertEquals("上午好", before.opening.headline)

        settingsStore.setAddress("王老师")

        awaitUntil { (mirror.value as? HomeUiState.Ready)?.needsAddress == false }
        val after = awaitReady()
        assertEquals("王老师，上午好", after.opening.headline)
        // 改称呼只动问候语，不动判定结果。
        assertEquals(before.opening.note, after.opening.note)
    }

    // ---- AC-03.1：跨时段边界靠刷新重读，而不是重判 ----

    @Test
    fun `跨时段边界靠刷新重读，但不动账本`() = runBlocking(Dispatchers.IO) {
        openHome()
        assertEquals("上午好", awaitReady().opening.headline)

        now = LocalDateTime.of(2026, 3, 10, 12, 30)
        viewModel.refresh()
        awaitUntil { (mirror.value as? HomeUiState.Ready)?.opening?.headline == "中午好" }

        // 判定结果还是那一次露面的那一个：只换了问候语，没有重新开口、也没有重新记账。
        // 先结算一下再读账本：问候语会随着刷新立刻变，而记账是另一条路——落笔的早晚不该由速度决定。
        settleQuietly()
        assertEquals(LocalDateTime.of(2026, 3, 10, 9, 0), interventionStore.stateOnce().lastSpokenAt)
    }

    // ---- AC-04.4 / 有画像时按用户自己的作息说话 ----

    @Test
    fun `有画像时按用户自己的作息说话`() = runBlocking(Dispatchers.IO) {
        // 直接预置一份画像：作息日的分界点是凌晨 4 点，09:00 落在 2026-03-10 这一天。
        rhythmRepository.saveProfile(
            LocalDate.of(2026, 3, 10).toEpochDay(),
            DailyRhythm(
                wakeAt = LocalTime.of(7, 0),
                sleepAt = LocalTime.of(22, 0),
                activeSpans = emptyList(),
                idleSpans = emptyList(),
                nightChargingAt = null,
                sampleDays = 5,
                confidence = 0.8f,
            ),
        )

        openHome()
        val ready = awaitReady()
        assertEquals("上午好", ready.opening.headline)
        // 按用户自己的作息说，就不补「我先按普通的作息来」那句（那是没画像时的话）。
        assertEquals("第一次见面，慢慢来。", ready.opening.note)

        // 22:15 是这份画像的睡觉时间，但**不是**兜底作息（22:30 起）的。
        // 这一条因此能区分「用的是用户自己的安静时段」和「用的是兜底安静时段」。
        now = LocalDateTime.of(2026, 3, 10, 22, 15)
        viewModel.onStop()
        viewModel.onStart()

        awaitOpening("这会儿是您休息的时间。")
        assertNull((mirror.value as HomeUiState.Ready).opening.anchorLine)
    }

    // ---- 工具 ----

    /**
     * 支起一次首页：真的 [ViewModelStore] + 真的视图模型，并把 `state` **常驻订阅**下来。
     *
     * 不在 `@Before` 里做，是因为有几个用例要在第一次露面之前先往库里 / DataStore 里写东西
     * （预置画像、先记一条锚点），次数与时机各不相同。
     */
    private fun openHome() {
        viewModelStore = ViewModelStore()
        viewModel = ViewModelProvider(
            viewModelStore,
            object : ViewModelProvider.Factory {
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    require(modelClass == HomeViewModel::class.java) { "这个替身工厂只造 HomeViewModel，来的是 $modelClass" }
                    @Suppress("UNCHECKED_CAST")
                    return HomeViewModel(
                        settings = settingsStore,
                        greetingEngine = greetingEngine,
                        clock = clock,
                        interventions = interventionStore,
                        profileService = profileService,
                        anchors = anchorRepository,
                        config = config,
                    ) as T
                }
            },
        )[HomeViewModel::class.java]

        mirror = MutableStateFlow(HomeUiState.Loading)
        observerScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
        observerJob = observerScope.launch { viewModel.state.collect { mirror.value = it } }
    }

    /** 等到首页读出了这次的结论（判定 + 问候语都算完了）。 */
    private suspend fun awaitReady(): HomeUiState.Ready {
        awaitUntil { mirror.value is HomeUiState.Ready }
        val ready = mirror.value
        check(ready is HomeUiState.Ready) { "期望 Ready，实际是 $ready" }
        return ready
    }

    /** 等到首页顶上那句话变成 [headline]。用于「该变的」那一类断言。 */
    private suspend fun awaitOpening(headline: String) {
        awaitUntil { (mirror.value as? HomeUiState.Ready)?.opening?.headline == headline }
    }

    /** 等一件**会**发生的事，超时就红。 */
    private suspend fun awaitUntil(timeoutMillis: Long = 5_000, condition: suspend () -> Boolean) {
        withTimeout(timeoutMillis) { while (!condition()) delay(10) }
    }

    /**
     * 给「不该变」的断言一段固定的等待窗口。
     *
     * 变了的事有信号可以等，不变的事没有，只能给时间。这里两个动作：
     * 先空排队到主线程一次，把已经派发出去的视图模型协程跑完（它们都在 `Main.immediate` 上）；
     * 再等 300ms——比一次 `DataStore.edit` 加一次重读大两个数量级，而本用例里没有别的长任务在跑。
     *
     * **不调 `waitForIdleSync`**：它会阻塞调用线程直到主线程空闲，而这里正好是在主线程上
     * 排队，从主线程调它等于自己等自己。
     */
    private suspend fun settleQuietly() {
        withContext(Dispatchers.Main) { }
        delay(300)
    }
}
