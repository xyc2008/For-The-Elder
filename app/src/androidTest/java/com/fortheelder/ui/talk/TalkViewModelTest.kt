package com.fortheelder.ui.talk

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
import com.fortheelder.data.wish.WishRepository
import com.fortheelder.domain.companion.AnchorSlot
import com.fortheelder.domain.companion.Encouragement
import com.fortheelder.domain.companion.StarterCatalog
import com.fortheelder.domain.engine.intent.IntentKind
import com.fortheelder.domain.wish.WishDecision
import com.fortheelder.domain.wish.WishStatus
import com.fortheelder.ui.关库前等收尾落地
import com.fortheelder.ui.取消后等收完
import java.io.File
import java.time.LocalDateTime
import java.time.ZoneId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 「说两句」这一页写入路径上，那几条被文档写死的不变量。
 *
 * 这里验的**不是界面长得对不对**（那是 `TalkScreenTest` 的活），而是只有接上真数据库
 * 才成立的事。理由：这一页的状态一半来自 Room 的 Flow、一半活在内存里，而三条承诺
 * 都明确要求落在**写入路径**上、不能只靠界面自觉——
 * - AC-07.2「继续对话不算表态」；
 * - AC-07.4「闸门独立于识别」；
 * - AC-09.1「不许凭自动识别建锚点」。
 *
 * 用的就是 App 自己在用的那几个仓库（`WishRepository` / `AnchorRepository` /
 * `InterventionStore`），不是替身：正例要看「真的写进库了没有」，替身只能证明调用发生。
 * 所以这个类只能跑在设备上——`TalkViewModel` 的构造参数都是要 Room 的具体类，
 * JVM 侧建不出来。
 *
 * 两个约定，写在前面免得后来人误会用例在等什么：
 * 1. **变了的事**用 [awaitUntil] 等出结果，`remembered` / `anchors` 由 Room 的 Flow
 *    回灌，落库和在页面上看见之间隔着一次异步派发；
 * 2. **不该变的事**没有可等的信号，只能用 [settleQuietly] 给一段固定的等待窗口。
 *    这是本文件唯一一处「靠时间」的地方，值取得比同类写入大两个数量级。
 */
@RunWith(AndroidJUnit4::class)
class TalkViewModelTest {

    private lateinit var context: Context
    /** 自己的库文件，不碰应用那个 `elder.db`；原因见 `ElderDatabase.open` 的注释。 */
    private val testDb = "elder-test-talk-vm.db"

    private lateinit var database: ElderDatabase
    private lateinit var wishRepository: WishRepository
    private lateinit var anchorRepository: AnchorRepository
    private lateinit var interventionStore: InterventionStore

    private lateinit var dataStoreFile: File
    private lateinit var storeScope: CoroutineScope
    private lateinit var dataStore: DataStore<Preferences>

    /** 视图模型装在真的 store 里，`@After` 才能走到 `onCleared`，两条 collector 才收得掉。 */
    private lateinit var viewModelStore: ViewModelStore
    private lateinit var viewModel: TalkViewModel

    /** 固定时区与固定「现在」：不用系统默认值，免得跑测试的机器换个时区结果就变。 */
    private val zone: ZoneId = ZoneId.of("Asia/Shanghai")
    private var now: LocalDateTime = LocalDateTime.of(2026, 3, 10, 9, 0)

    /** 一个目录里真的有的起点，用来做正例。 */
    private val paper: String = "calligraphy-paper"

    /** 能被识别出来的示例句，命中的是 INTEREST / 书法。 */
    private val calligraphySentence: String = "我一直想学书法"

    @Before
    fun setUp() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        context.deleteDatabase(testDb)
        database = ElderDatabase.open(context, testDb)

        val clock = AppClock { now }
        wishRepository = WishRepository(database, clock, zone)
        anchorRepository = AnchorRepository(database, clock, zone)

        // 真的 DataStore 文件而不是内存假件：`recordAnswered` 的落盘本身也在被测范围内。
        // 文件名每次唯一，因为同一个文件上不能同时有两个 DataStore 活着。
        dataStoreFile = File(context.cacheDir, "talk-vm-${System.nanoTime()}.preferences_pb")
        storeScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        dataStore = PreferenceDataStoreFactory.create(scope = storeScope) { dataStoreFile }
        interventionStore = InterventionStore(dataStore, clock, zone)

        viewModelStore = ViewModelStore()
        viewModel = ViewModelProvider(
            viewModelStore,
            object : ViewModelProvider.Factory {
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    require(modelClass == TalkViewModel::class.java) { "这个替身工厂只造 TalkViewModel，来的是 $modelClass" }
                    @Suppress("UNCHECKED_CAST")
                    return TalkViewModel(wishRepository, interventionStore, anchorRepository) as T
                }
            },
        )[TalkViewModel::class.java]
    }

    @After
    fun tearDown() {
        // 先收摊再关库：collector 还挂在 Room 的 Flow 上时关库，异常会打在没人接的
        // 协程里，变成一条跟被测代码无关的崩溃。
        //
        // 但「先」不等于「等到了」：`viewModelStore.clear()` 一返回就走人，Room 那边的
        // 观察者还挂着（第 34 轮实测约 104 ms 才摘干净），撞上关库就是
        // `attempt to re-open an already-closed object`。所以两步之间要主动等落地。
        viewModelStore.clear()
        storeScope.cancel()
        取消后等收完(storeScope)
        关库前等收尾落地(database)
        database.close()
        context.deleteDatabase(testDb)
        dataStoreFile.delete()
    }

    // ---- AC-07.2 / AC-07.4：听出来的东西不自动算数 ----

    @Test
    fun `听出来的意愿先是没表态的`() = runBlocking(Dispatchers.IO) {
        viewModel.hear(calligraphySentence)

        // 识别是同步的：这一句回来之后马上就能看到「听到了什么」。
        // 这也是 AC-07.4 说的那个切分——[TalkUiState.Heard] 只负责听出了什么。
        val heard = viewModel.page.value.heard
        assertTrue("期望 Heard，实际是 $heard", heard is TalkUiState.Heard)

        waitForRemembered("书法")
        // 「要不要记」是另一个维度：识别结果不产生「已同意」。
        assertEquals(WishStatus.PENDING, viewModel.page.value.statusOf(IntentKind.INTEREST, "书法"))
        // AC-07.1：没表态的可以问一次，但内容一律不放行。
        assertEquals(listOf("书法"), viewModel.page.value.toConfirm.map { it.term })
        assertTrue("没确认过的意愿不该配上起点", viewModel.page.value.offerableWishes.isEmpty())
        // 它真的落库了，而不是只活在内存里。
        assertEquals(1, wishRepository.count())
    }

    @Test
    fun `又提一遍同一件事不算表态也不重置状态`() = runBlocking(Dispatchers.IO) {
        viewModel.hear(calligraphySentence)
        waitForRemembered("书法")
        viewModel.decide(IntentKind.INTEREST, "书法", WishDecision.DECLINE)
        waitForStatus("书法", WishStatus.DECLINED)

        // 同一件事再说一遍（AC-07.2：继续说话不是表态）。
        viewModel.hear(calligraphySentence)
        settleQuietly()

        assertEquals(WishStatus.DECLINED, viewModel.page.value.statusOf(IntentKind.INTEREST, "书法"))
        assertEquals("重复提到不该多出一条", 1, wishRepository.count())
        // AC-07.3：已经说了不用的，不许再被主动提起。
        assertTrue(viewModel.page.value.toConfirm.isEmpty())
    }

    @Test
    fun `又说了别的不会带倒已经定下来的状态`() = runBlocking(Dispatchers.IO) {
        confirmCalligraphy()

        viewModel.hear("想学用手机")
        // 同步的那一半：屏幕上立刻换成这一句。
        val heard = viewModel.page.value.heard
        assertTrue("期望 Heard，实际是 $heard", heard is TalkUiState.Heard && heard.sentence == "想学用手机")

        waitForStatus("用手机", WishStatus.PENDING)
        // 新说的事只是新的一条，带不动旧的那一条。
        assertEquals(WishStatus.CONFIRMED, viewModel.page.value.statusOf(IntentKind.INTEREST, "书法"))
        assertEquals(2, wishRepository.count())
    }

    @Test
    fun `空话不改变屏幕上显示的东西`() {
        val before = viewModel.page.value

        viewModel.hear("")
        viewModel.hear("   ")

        // 整页状态按值相等：不光是「没记下来」，连「说出了什么」「输入框」都不该动。
        assertEquals(before, viewModel.page.value)
    }

    @Test
    fun `说了没被听懂的话也算接话，但没有意愿落库`() = runBlocking(Dispatchers.IO) {
        val nonsense = TalkExamples.notRecognized.first()
        assertNull(interventionStore.stateOnce().lastAnsweredAt)

        viewModel.hear(nonsense)

        // AC-06.2：听不出来就说听不出来，而不是留一个空白的「听到了」。
        assertEquals(TalkUiState.NotUnderstood(nonsense), viewModel.page.value.heard)
        // 「愿意开口」这件事本身要被记下来（FR-05），和「说了什么」无关。
        awaitUntil { interventionStore.stateOnce().lastAnsweredAt != null }
        settleQuietly()
        assertEquals(0, wishRepository.count())
        assertTrue(viewModel.page.value.remembered.isEmpty())
    }

    // ---- AC-09.1：锚点只能由用户亲手建（四条守卫逐条打） ----

    @Test
    fun `库里一条意愿都没有时点时段也不建锚点`() = runBlocking(Dispatchers.IO) {
        // 这一条打的是 statusOf 返回 null 的那条守卫：意愿根本还没落到页面上。
        assertNull(viewModel.page.value.statusOf(IntentKind.INTEREST, "书法"))
        viewModel.proposeAnchor(paper)
        assertEquals(paper, viewModel.page.value.anchorAskFor)

        viewModel.confirmAnchor(paper, AnchorSlot.MORNING)
        settleQuietly()

        assertEquals(0, anchorRepository.count())
        assertTrue(viewModel.page.value.anchors.isEmpty())
        // 被拦下也没把问句撤掉：真正的按钮还在用户面前，他还能再点一次。
        assertEquals(paper, viewModel.page.value.anchorAskFor)
    }

    @Test
    fun `没确认过的时候点了时段也不建锚点`() = runBlocking(Dispatchers.IO) {
        viewModel.hear(calligraphySentence)
        waitForRemembered("书法")
        assertEquals(WishStatus.PENDING, viewModel.page.value.statusOf(IntentKind.INTEREST, "书法"))

        viewModel.proposeAnchor(paper)
        assertEquals(paper, viewModel.page.value.anchorAskFor)
        viewModel.confirmAnchor(paper, AnchorSlot.MORNING)
        settleQuietly()

        assertEquals(0, anchorRepository.count())
        assertTrue(viewModel.page.value.anchors.isEmpty())
    }

    @Test
    fun `说了不用的意愿也推不出锚点`() = runBlocking(Dispatchers.IO) {
        viewModel.hear(calligraphySentence)
        waitForRemembered("书法")
        viewModel.decide(IntentKind.INTEREST, "书法", WishDecision.DECLINE)
        waitForStatus("书法", WishStatus.DECLINED)

        viewModel.proposeAnchor(paper)
        viewModel.confirmAnchor(paper, AnchorSlot.MORNING)
        settleQuietly()

        assertEquals(0, anchorRepository.count())
    }

    @Test
    fun `确认过之后点时段才真的建，而且只问一次`() = runBlocking(Dispatchers.IO) {
        confirmCalligraphy()
        // 确认过的「想做的事」才配得上起点，而且只有这一条。
        assertEquals(listOf("书法"), viewModel.page.value.offerableWishes.map { it.term })

        viewModel.proposeAnchor(paper)
        assertEquals(paper, viewModel.page.value.anchorAskFor)
        // 问时段的同时把起点摊开，用户看得见自己在把哪一步记成日常。
        assertEquals(paper, viewModel.page.value.openOption)
        viewModel.confirmAnchor(paper, AnchorSlot.AFTERNOON)

        awaitUntil { anchorRepository.count() == 1 }
        val anchor = anchorRepository.anchors.first().single()
        assertEquals("书法", anchor.term)
        assertEquals(paper, anchor.optionId)
        assertEquals(AnchorSlot.AFTERNOON, anchor.slot)
        // AC-08.1~08.3：锚点的动作就是从起点继承来的那一步原文，不是另写一份。
        assertEquals(StarterCatalog.byId(paper)!!.firstStep, anchor.action)
        // 问过一次就不再问：时段按钮收回去。
        assertNull(viewModel.page.value.anchorAskFor)
        // 页面上那条也是从库里发出来的，不是自己拼的。
        awaitUntil { viewModel.page.value.anchors.size == 1 }
        assertEquals(paper, viewModel.page.value.anchorOf(paper)?.optionId)
    }

    @Test
    fun `目录里没有的起点点不出来也建不了`() = runBlocking(Dispatchers.IO) {
        confirmCalligraphy()

        viewModel.proposeAnchor("calligraphy")
        assertNull("目录里没有的 id 不该问出时段", viewModel.page.value.anchorAskFor)

        viewModel.confirmAnchor("calligraphy", AnchorSlot.MORNING)
        viewModel.markDone("calligraphy")
        settleQuietly()

        // 就算意愿已经确认过，一个手写的 id 也造不出锚点、要不来一句回话。
        assertEquals(0, anchorRepository.count())
        assertTrue(viewModel.page.value.encouragements.isEmpty())
    }

    // ---- AC-10：做完一件事只回一句话 ----

    @Test
    fun `做完了只是回一句话，不留记录`() = runBlocking(Dispatchers.IO) {
        confirmCalligraphy()
        viewModel.proposeAnchor(paper)
        viewModel.confirmAnchor(paper, AnchorSlot.ANYTIME)
        awaitUntil { anchorRepository.count() == 1 }

        viewModel.markDone(paper)

        val said = viewModel.page.value.encouragements[paper]
        assertNotNull("点「做完了」要有一句回话", said)
        val step = StarterCatalog.byId(paper)!!.firstStep
        // AC-10.1：回话必须指的是刚做完的那件事本身；AC-10.2/10.3 的边界一并扫掉。
        assertTrue("回话里没有那件事本身", said!!.contains(step))
        assertEquals(emptyList<String>(), Encouragement.violations(said, step))

        settleQuietly()
        // 做完不留记录：库里既没有多一条锚点，也没有多一条意愿。
        assertEquals(1, anchorRepository.count())
        assertEquals(1, wishRepository.count())
    }

    // ---- AC-09.3：删掉就是删掉 ----

    @Test
    fun `删掉一条日常就是删掉，不留痕迹也不追问`() = runBlocking(Dispatchers.IO) {
        confirmCalligraphy()
        viewModel.proposeAnchor(paper)
        viewModel.confirmAnchor(paper, AnchorSlot.MORNING)
        awaitUntil { anchorRepository.count() == 1 }

        // 又把时段问出来，验「删的时候顺手把问句收回」。
        viewModel.proposeAnchor(paper)
        assertEquals(paper, viewModel.page.value.anchorAskFor)

        viewModel.forgetAnchor(paper)

        // 问句当场收回，不等落库。
        assertNull(viewModel.page.value.anchorAskFor)
        awaitUntil { anchorRepository.count() == 0 }
        awaitUntil { viewModel.page.value.anchors.isEmpty() }
        // 删完就干净了：没有「已删除」的痕迹，也没有「要不要再记回来」的反向提问。
        assertTrue(viewModel.page.value.encouragements.isEmpty())
    }

    // ---- 摊开是纯界面动作 ----

    @Test
    fun `一次只摊开一个起点`() {
        viewModel.toggleOption(paper)
        assertEquals(paper, viewModel.page.value.openOption)

        viewModel.toggleOption(paper)
        assertNull("再点一次就是收起来", viewModel.page.value.openOption)

        viewModel.toggleOption(paper)
        viewModel.toggleOption("calligraphy-pen")
        assertEquals("摊开第二个就把第一个收起来", "calligraphy-pen", viewModel.page.value.openOption)

        // 摊开不代表答应了什么：没落库、没锚点、也没算表态。
        assertTrue(viewModel.page.value.anchors.isEmpty())
        assertEquals(emptyList<String>(), viewModel.page.value.remembered.map { it.term })
    }

    // ---- 工具 ----

    /** 说一句「我一直想学书法」并等到它确认下来，正例们的前置。 */
    private suspend fun confirmCalligraphy() {
        viewModel.hear(calligraphySentence)
        waitForRemembered("书法")
        viewModel.decide(IntentKind.INTEREST, "书法", WishDecision.CONFIRM)
        waitForStatus("书法", WishStatus.CONFIRMED)
    }

    /** 等这条意愿出现在页面上——它是由 Room 的 Flow 异步回灌的。 */
    private suspend fun waitForRemembered(term: String) {
        awaitUntil { viewModel.page.value.remembered.any { it.term == term } }
    }

    private suspend fun waitForStatus(term: String, status: WishStatus) {
        awaitUntil { viewModel.page.value.statusOf(IntentKind.INTEREST, term) == status }
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
     * 再等 300ms——比一次 `DataStore.edit` 加一次 Room `insert` 大两个数量级，
     * 而本用例里没有别的长任务在跑。
     *
     * **不调 `waitForIdleSync`**：它会阻塞调用线程直到主线程空闲，而这里正好是在主线程上
     * 排队，从主线程调它等于自己等自己。
     */
    private suspend fun settleQuietly() {
        withContext(Dispatchers.Main) { }
        delay(300)
    }
}
