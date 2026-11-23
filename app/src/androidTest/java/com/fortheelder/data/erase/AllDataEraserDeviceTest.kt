package com.fortheelder.data.erase

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.fortheelder.core.AppContainer
import com.fortheelder.data.prefs.AppSettings
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
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/**
 * 「清空全部数据」在**真存储**上到底清没清干净（FR-14 / AC-14.3）。
 *
 * ## 它补的是哪半边
 *
 * 第 71 轮把设置页那颗按钮从「清空设置」改成「清空全部数据」时，「清空」这件事分三处守：
 *
 * 1. `AllDataEraserTest`（JVM）拿五个记录调用的小 lambda 钉死「五类各被调到一次、顺序对」；
 * 2. `EraseAllWiringTest`（JVM）对着 `AppContainer.kt` 的**源码文本**守「容器把每一条接到哪个仓库」；
 * 3. `SettingsScreenTest.确认清空设置之后就回到引导页`（设备层）点那颗按钮，守「点下去之后回到引导页」。
 *
 * 三条都不管**真库和真偏好文件里那五处是不是真的空了**：第 1、2 条手上没有真存储，
 * 第 3 条只断言「回到引导页 + `onboardingDone=false` + `address=null`」，一行 Room 表都没碰、
 * 一个 `intervention` 键都没看。设备层原有那几条清空用例（`AnchorRepositoryTest`、
 * `WishRepositoryTest`、`RhythmRepositoryTest`）各管一张表，而且都是**自己 `new` 一个仓库**、
 * 直接调它的 `clearAll()`，不走容器。
 *
 * 于是「容器那五条 lambda 在真存储上跑一遍，五处是否一起归零」这件事，一直没人验过。
 * 这个类就走**容器驱动**：真 `AppContainer` + 真 `ElderDatabase.open` + 真两份 `DataStore`，
 * 调一次 `container.allDataEraser.erase()`，把五处一起看一遍。
 *
 * ## 为什么走容器而不是走界面
 *
 * 「点按钮 → 擦除器」这一环已经由上面第 3 条行为覆盖，「接线到哪个仓库」由第 2 条文本覆盖，
 * 缺的正是中间那一段。再套一层 `createComposeRule` 只会把这三段搅在一起，
 * 还要多担一份界面时序的抖动。这里只有存储，没有界面。
 *
 * ## 为什么每条用例都先断言「清空前非空」
 *
 * 「删完之后是空的」这件事，在一个**本来就空**的存储上照样成立——播种失败、字段写错、
 * 库没建起来，用例都会静默变绿。所以每条用例的第一段都是把五处依次写成非空并读懂，
 * 清空之后再看一遍。少了这一段，这两条用例就是两个假的绿勾。
 *
 * 库文件用自己的 `elder-test-all-data-eraser.db`，两份偏好走 `自己的存储` 造的临时文件，
 * 不碰这台设备上应用真正在用的 `elder.db` 与那两份偏好。
 */
@RunWith(AndroidJUnit4::class)
class AllDataEraserDeviceTest {

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
     * 五处齐备 → 一次 `erase()` → 五处一起归零。
     *
     * 五处就是 AC-14.3 那句「全部本地数据」的全部内容：`wish` / `anchor` /
     * `rhythm_signal` / `daily_rhythm` 四张表和 `intervention` 这个偏好文件里的节流状态，
     * 外加 `settings` 这份偏好。**下面每一项断言都指名道姓写到具体的那一处**，
     * 不用「条数之和」之类能互相掩盖的口径——漏清一处，就得有一条断言红。
     */
    @Test
    fun `五处都有东西时一次清空全都空`() = runBlocking(Dispatchers.IO) {
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

        container.allDataEraser.erase()

        assertEquals("意愿没清", 0, container.wishRepository.count())
        assertEquals("日常没清", 0, container.anchorRepository.count())
        assertEquals("作息信号没清", 0, container.rhythmRepository.signalCountOnce())
        assertNull("作息画像没清", container.rhythmRepository.latestProfile())
        val 节流 = container.interventionStore.stateOnce()
        assertNull("节流状态里还留着『上次开口』", 节流.lastSpokenAt)
        assertNull("节流状态里还留着『上次被接话』", 节流.lastAnsweredAt)
        assertEquals("节流状态里还留着忽略计数", 0, 节流.ignoredStreak)
        assertEquals("设置不是出厂状态", AppSettings(), container.settings.settings.first())

        assertTrue("清空数据不该把偏好文件本身删掉", 存储.settingsFile.exists())
        assertTrue("清空数据不该把偏好文件本身删掉", 存储.interventionFile.exists())
    }

    /**
     * 清空之后这五处还能接着用。
     *
     * 这一条守的是「清空 = 把数据删掉」，而不是「砸库 / 删文件 / 把 DataStore 锁死」。
     * 用户可见的那条链路是「清空全部数据 → 重新走一遍引导 → 接着用」，中间那一步要是
     * 把库砸了，用户点完按钮就再也存不进东西——而他点这颗按钮的意思是「重新开始」，
     * 不是「别再用了」。所以清空之后每一处都再写一条、再读回来。
     */
    @Test
    fun `清空之后这五处还能接着用`() = runBlocking(Dispatchers.IO) {
        container.wishRepository.remember(SENTENCE, hitsOf(SENTENCE))
        container.interventionStore.recordSpoken()
        container.settings.setAddress(旧称呼)

        container.allDataEraser.erase()

        container.wishRepository.remember(SENTENCE, hitsOf(SENTENCE))
        assertTrue(
            "清空之后日常应该还能记",
            container.anchorRepository.add(AnchorRules.draftOf("chess-solo", AnchorSlot.ANYTIME)!!),
        )
        container.rhythmRepository.record(listOf(CapturedSignal(采集时刻, RhythmKind.SCREEN_ON)))
        container.rhythmRepository.saveProfile(画像那天, rhythm())
        container.interventionStore.recordSpoken()
        container.settings.setAddress(新称呼)

        assertEquals("清空之后意愿写不进去", 1, container.wishRepository.count())
        assertEquals("清空之后日常写不进去", 1, container.anchorRepository.count())
        assertEquals("清空之后作息信号写不进去", 1, container.rhythmRepository.signalCountOnce())
        assertNotNull("清空之后作息画像写不进去", container.rhythmRepository.latestProfile())
        assertNotNull("清空之后节流状态写不进去", container.interventionStore.stateOnce().lastSpokenAt)
        assertEquals("清空之后设置写不进去", 新称呼, container.settings.settings.first().address)
    }

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
        const val TEST_DB = "elder-test-all-data-eraser.db"
        const val SENTENCE = "我一直想学书法"
        const val 旧称呼 = "老熊"
        const val 新称呼 = "熊勇春"
        val 采集时刻: LocalDateTime = LocalDateTime.of(2026, 3, 9, 7, 0)
        val 画像那天: Long = LocalDate.of(2026, 3, 9).toEpochDay()
    }
}
