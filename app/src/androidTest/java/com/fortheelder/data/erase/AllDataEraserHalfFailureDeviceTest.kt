package com.fortheelder.data.erase

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.fortheelder.core.AppContainer
import com.fortheelder.data.rhythm.CapturedSignal
import com.fortheelder.domain.companion.AnchorRules
import com.fortheelder.domain.companion.AnchorSlot
import com.fortheelder.domain.engine.intent.IntentScan
import com.fortheelder.domain.engine.intent.IntentScanner
import com.fortheelder.domain.engine.rhythm.DailyRhythm
import com.fortheelder.domain.engine.rhythm.RhythmKind
import com.fortheelder.domain.engine.rhythm.TimeSpan
import com.fortheelder.ui.自己的存储
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/**
 * 「清空全部数据」**在真存储上**中途失手时，库和偏好各停在哪儿（FR-14 / AC-14.3 / R-15）。
 *
 * ## 它补的是哪半边
 *
 * [AllDataEraser] 的类注释里写着一条诚实交代：它**不是原子操作**，中途失败会留下
 * 「删了一部分」的状态，而它选择的处置是**不吞异常、后面的步骤不再执行**、界面停在设置页
 * （这条残余登记在 `docs/07-风险管理计划.md` 的 R-15）。
 *
 * 第 71 轮给这句话在 JVM 层配了证据：`AllDataEraserTest` 的第 3 例
 * （`中途出错时后面的步骤不再执行 异常也不吞`）拿五个**记录调用的小 lambda** 把
 * 「顺序停在半路 + 异常原样抛出」钉死。可那一例手上全是 lambda，没有一行真 Room 表、
 * 没有一个真 DataStore 键——它证明的是 `erase()` 这段顺序代码本身，
 * **不证明「真存储停在半路时，前几步真的落了盘、后几步真的一点没动」**。
 *
 * 这个类就走真存储把那半补上：真 `AppContainer` + 真 `ElderDatabase.open` + 真两份
 * `DataStore`，只把第三步的 lambda 换成**人为抛异常**，其余四步接的仍是容器里那四条
 * **真的**清理调用（`wishRepository.forgetAll()` / `anchorRepository.clearAll()` /
 * `interventionStore.reset()` / `settings.resetToDefaults()`）。
 *
 * ## 为什么是「换掉第三步」而不是「换掉第五步」
 *
 * 换第三步（作息）能把「半清」摆成三段：前两步（意愿、日常）**该真清掉**、
 * 第三步自己**该没动手**、后两步（节流、设置）**该真没跑**。换最后一步只能验到
 * 「前四步做了、最后一步没做」，少了中间「抛出的那一步自己没做事」这一段。
 *
 * ## 为什么每条用例都先断言「起点非空」
 *
 * 与 `AllDataEraserDeviceTest` 同一个理由：「删完之后还是空的」在一个本来就空的存储上
 * 照样成立，播种失败、字段写错、库没建起来，用例都会静默变绿。所以每条用例都先把五处
 * 写成非空并逐处读懂，再动手。
 *
 * 库文件用自己的 `elder-test-half-failure.db`，两份偏好走 `自己的存储` 造的临时文件，
 * 不碰这台设备上应用真正在用的 `elder.db` 与那两份偏好。
 */
@RunWith(AndroidJUnit4::class)
class AllDataEraserHalfFailureDeviceTest {

    private lateinit var context: Context
    private lateinit var 存储: 自己的存储
    private lateinit var container: AppContainer

    @Before
    fun setUp() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        context.deleteDatabase(TEST_DB)
        存储 = 自己的存储(context, TEST_DB)
        container = AppContainer(context, TEST_DB, 存储.settingsData, 存储.interventionData)
    }

    @After
    fun tearDown() {
        container.database.close()
        context.deleteDatabase(TEST_DB)
    }

    /**
     * 五处齐备 → 第三步人为抛异常 → 停在半路，前两步真清、后两步真没跑。
     *
     * 每一步的落点都**指名道姓**断言到具体那一处，不用「条数之和」之类能互相掩盖的口径：
     * 前两步看真 Room 表的行数归零；第三步看真作息信号表与真画像表**原样还在**；
     * 后两步看真 `intervention` 偏好里的键与真 `settings` 偏好里的称呼、引导标记**原样还在**。
     * 少一条断言，就少一处能被「改错了也照样绿」蒙过去的地方。
     */
    @Test
    fun `第三步抛异常时前两步真清了后面几步真没跑`() = runBlocking(Dispatchers.IO) {
        五处都播种好()

        val 崩了 = IllegalStateException("第三步（作息）人为失手")
        val 半成品 = 中途会崩的擦除器(崩了)

        val 抓到的 = runCatching { 半成品.erase() }.exceptionOrNull()

        assertSame("异常应该原样抛出去：不许换成别的、也不许吞掉", 崩了, 抓到的)
        assertEquals("第一步（意愿）应该已经真清掉", 0, container.wishRepository.count())
        assertEquals("第二步（日常）应该已经真清掉", 0, container.anchorRepository.count())
        assertTrue(
            "第三步自己抛在前面，作息信号不该被动过",
            container.rhythmRepository.signalCountOnce() > 0,
        )
        assertNotNull("第三步自己抛在前面，作息画像不该被动过", container.rhythmRepository.latestProfile())
        assertNotNull(
            "第四步没轮上，节流状态里『上次开口』不该被清掉",
            container.interventionStore.stateOnce().lastSpokenAt,
        )
        assertEquals(
            "第五步没轮上，设置里的称呼不该被动过",
            旧称呼,
            container.settings.settings.first().address,
        )
        assertTrue(
            "第五步没轮上，设置里的引导标记不该被动过",
            container.settings.settings.first().onboardingDone,
        )
    }

    /**
     * 停在半路之后**再点一次**「清空全部数据」，五处能真的清干净。
     *
     * 这一条守的是 R-15 那句处置的落脚点：「宁可停在半路让用户再点一次」。要是半清状态
     * 本身有毒——比如第一步把库锁死了、或者中途失败让 `erase()` 第二次也起不来——
     * 那句「再点一次」就是空话。所以这里先人为失手一次、确认确实停在半路，再走
     * **真的容器擦除器**（`container.allDataEraser`，五条接线全是真的）跑一遍，
     * 把五处归零看一遍。
     */
    @Test
    fun `停在半路之后再清一次还能清干净`() = runBlocking(Dispatchers.IO) {
        五处都播种好()

        val 崩了 = IllegalStateException("第三步（作息）人为失手")
        runCatching { 中途会崩的擦除器(崩了).erase() }

        assertEquals("失手之后意愿应该已经空了（第一步真跑了）", 0, container.wishRepository.count())
        assertTrue(
            "失手之后作息数据应该还在（第三步没兑现）",
            container.rhythmRepository.signalCountOnce() > 0,
        )

        container.allDataEraser.erase()

        assertEquals("重清之后意愿没清", 0, container.wishRepository.count())
        assertEquals("重清之后日常没清", 0, container.anchorRepository.count())
        assertEquals("重清之后作息信号没清", 0, container.rhythmRepository.signalCountOnce())
        assertNull("重清之后作息画像没清", container.rhythmRepository.latestProfile())
        assertNull(
            "重清之后节流状态里还留着『上次开口』",
            container.interventionStore.stateOnce().lastSpokenAt,
        )
        assertNull(
            "重清之后设置里的称呼没回到出厂状态",
            container.settings.settings.first().address,
        )
        assertFalse(
            "重清之后设置里的引导标记没回到出厂状态",
            container.settings.settings.first().onboardingDone,
        )
    }

    /**
     * 把五处依次写成非空，并逐处读懂——起点若没建立起来，后面所有「空」都不可信。
     *
     * 四条真清理 lambda 直接取容器里那四条同一份调用，第三步换成抛异常：这样前两步
     * 与后两步走的是**生产同一条路径**，被观察的正是 `erase()` 的「顺序 + 不吞异常」，
     * 而不是某个测试替身的行为。
     */
    private suspend fun 五处都播种好() {
        container.wishRepository.remember(SENTENCE, hitsOf(SENTENCE))
        assertTrue(
            "起点：一条日常应该能记下",
            container.anchorRepository.add(AnchorRules.draftOf("calligraphy-paper", AnchorSlot.MORNING)!!),
        )
        container.rhythmRepository.record(listOf(CapturedSignal(采集时刻, RhythmKind.SCREEN_ON)))
        container.rhythmRepository.saveProfile(画像那天, rhythm())
        container.interventionStore.recordSpoken()
        container.settings.setAddress(旧称呼)
        container.settings.completeOnboarding()

        assertTrue("起点：意愿表里有东西", container.wishRepository.count() > 0)
        assertTrue("起点：日常表里有东西", container.anchorRepository.count() > 0)
        assertTrue("起点：作息信号表里有东西", container.rhythmRepository.signalCountOnce() > 0)
        assertNotNull("起点：作息画像表里有东西", container.rhythmRepository.latestProfile())
        assertNotNull("起点：节流状态里有『上次开口』", container.interventionStore.stateOnce().lastSpokenAt)
        assertEquals("起点：设置里有称呼", 旧称呼, container.settings.settings.first().address)
        assertTrue("起点：设置里引导已走完", container.settings.settings.first().onboardingDone)
    }

    /** 中间那条（[AllDataEraser] 的第三步）换成抛 [崩了] 的擦除器，其余四步接真调用。 */
    private fun 中途会崩的擦除器(崩了: Throwable) = AllDataEraser(
        clearWishes = { container.wishRepository.forgetAll() },
        clearAnchors = { container.anchorRepository.clearAll() },
        clearRhythm = { throw 崩了 },
        resetThrottle = { container.interventionStore.reset() },
        resetSettings = { container.settings.resetToDefaults() },
    )

    private fun rhythm() = DailyRhythm(
        wakeAt = LocalTime.of(6, 30),
        sleepAt = LocalTime.of(22, 30),
        activeSpans = listOf(TimeSpan(LocalTime.of(7, 0), LocalTime.of(11, 0))),
        idleSpans = listOf(TimeSpan(LocalTime.of(14, 0), LocalTime.of(16, 0))),
        nightChargingAt = LocalTime.of(23, 0),
        sampleDays = 5,
        confidence = 0.75f,
    )

    /** 走真的 [IntentScanner]，理由与 `WishRepositoryTest` 相同：手搓的命中会和真实输入脱节。 */
    private fun hitsOf(sentence: String) =
        (IntentScanner.scan(sentence) as IntentScan.Recognized).hits

    private companion object {
        const val TEST_DB = "elder-test-half-failure.db"
        const val SENTENCE = "我一直想学书法"
        const val 旧称呼 = "老熊"
        val 采集时刻: LocalDateTime = LocalDateTime.of(2026, 3, 9, 7, 0)
        val 画像那天: Long = LocalDate.of(2026, 3, 9).toEpochDay()
    }
}
