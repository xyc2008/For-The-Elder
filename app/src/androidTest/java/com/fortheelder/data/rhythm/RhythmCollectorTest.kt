package com.fortheelder.data.rhythm

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.fortheelder.ElderApp
import com.fortheelder.core.time.AppClock
import com.fortheelder.data.db.ElderDatabase
import com.fortheelder.domain.engine.rhythm.RhythmBroadcasts
import com.fortheelder.domain.engine.rhythm.RhythmKind
import java.io.FileInputStream
import java.time.LocalDateTime
import java.time.ZoneId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 采集器的设备侧验证。
 *
 * 大部分用例是**直接调 [RhythmCollector.onBroadcast]**，也就是把「广播已经送到了」
 * 当成前提，只验它拿到动作名之后做的事。这不是真实广播投递——真实路径由最后一条用例覆盖，
 * 它用 `input keyevent 26` 让系统自己把亮灭屏广播发出来，中间没有任何测试代码替它转手。
 * 两者缺一不可：前面几条证明映射和去重是对的，最后一条证明**注册本身是有效的**。
 *
 * 采集器只运行时注册（AC-04.5），所以这里也不用碰清单文件。
 */
@RunWith(AndroidJUnit4::class)
class RhythmCollectorTest {

    private lateinit var context: Context
    /** 自己的库文件，不碰应用那个 `elder.db`；原因见 `ElderDatabase.open` 的注释。 */
    private val testDb = "elder-test-rhythm-collector.db"

    private lateinit var database: ElderDatabase
    private lateinit var repository: RhythmRepository
    private lateinit var collector: RhythmCollector
    private lateinit var scope: CoroutineScope

    /** 固定「现在」：带上秒和纳秒，好让「同一时刻重复收到」这件事说得清楚。 */
    private val now: LocalDateTime = LocalDateTime.of(2026, 3, 10, 9, 0, 12, 345_000_000)

    @Before
    fun setUp() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        // 仪器化用例跑在应用进程里，所以 `ElderApp.onCreate` 已经把**进程级**采集器
        // 挂上了（若用户开着采集开关）。它用的是真实时钟、写的是同一张表，
        // 那一按电源键它也会跟着写两条，让下面「库是空的」这个前提和末尾那组
        // 时间戳断言一起失效——第 7 轮真撞上过：`expected:<[09:00:12.345]> but was:
        // <[…12.345, …2026-11-21T00:12:34.765, …34.833]>`。这里先把它停掉，把
        // 「谁在写库」收敛成一个，断言才有意义。停掉后不恢复：进程随用例结束而终止。
        (context.applicationContext as ElderApp).container.rhythmCollector.stop()
        context.deleteDatabase(testDb)
        database = ElderDatabase.open(context, testDb)
        repository = RhythmRepository(database, AppClock { now }, ZoneId.of("Asia/Shanghai"))
        scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        collector = RhythmCollector(context, repository, AppClock { now }, scope)
    }

    @After
    fun tearDown() {
        if (collector.isRunning) collector.stop()
        scope.cancel()
        database.close()
        context.deleteDatabase(testDb)
    }

    @Test
    fun `五种动作各自落一条对应种类的信号`() = runBlocking(Dispatchers.IO) {
        val expected = mapOf(
            RhythmBroadcasts.ACTION_SCREEN_ON to RhythmKind.SCREEN_ON,
            RhythmBroadcasts.ACTION_SCREEN_OFF to RhythmKind.SCREEN_OFF,
            RhythmBroadcasts.ACTION_USER_PRESENT to RhythmKind.USER_PRESENT,
            RhythmBroadcasts.ACTION_POWER_CONNECTED to RhythmKind.CHARGING_START,
            RhythmBroadcasts.ACTION_POWER_DISCONNECTED to RhythmKind.CHARGING_END,
        )

        expected.forEach { (action, _) -> collector.onBroadcast(action)?.join() }

        val stored = repository.signals()
        assertEquals(5, stored.size)
        // 五种动作都发生在同一个时刻，只能靠种类区分，所以这条也是唯一索引去重的依据。
        assertEquals(expected.values.toSet(), stored.map { it.kind }.toSet())
        assertEquals(now, stored.first().at)
    }

    @Test
    fun `认不出来的动作不落库也不返回任务`() = runBlocking(Dispatchers.IO) {
        assertNull(collector.onBroadcast("android.intent.action.BOOT_COMPLETED"))
        assertNull(collector.onBroadcast(null))
        assertNull(collector.onBroadcast(""))

        assertEquals(0, repository.signalCountOnce())
    }

    @Test
    fun `同一时刻的同一种动作收到两遍只算一条`() = runBlocking(Dispatchers.IO) {
        collector.onBroadcast(RhythmBroadcasts.ACTION_SCREEN_ON)?.join()
        collector.onBroadcast(RhythmBroadcasts.ACTION_SCREEN_ON)?.join()

        // 系统有时会把一次亮屏报两遍。重复事件会让「这天信号数」虚高，直接影响有效天的判定。
        assertEquals(1, repository.signalCountOnce())
    }

    @Test
    fun `开始之后isRunning为真停止之后为假`() {
        assertFalse(collector.isRunning)

        collector.start()
        assertTrue(collector.isRunning)

        collector.stop()
        assertFalse(collector.isRunning)
    }

    @Test
    fun `start和stop重复调用都是安全的`() {
        collector.start()
        collector.start()
        assertTrue(collector.isRunning)

        collector.stop()
        // 第二次 stop 不能抛：真的注销两遍会 IllegalArgumentException，进程会崩。
        collector.stop()
        assertFalse(collector.isRunning)
    }

    @Test
    fun `屏幕开关广播真的能从系统收到`() = runBlocking(Dispatchers.IO) {
        // 前提：库是空的。有了这个前提，「条数从 0 变成非 0」才等于「真的收到了投递」。
        assertEquals(0, repository.signalCountOnce())
        collector.start()

        // 两次电源键：无论开始时屏幕是亮是灭，中间至少会发生一次亮屏或灭屏。
        // 这一步是真实的系统广播投递——测试代码只按键，不替系统转发。
        shell("input keyevent 26")
        shell("input keyevent 26")

        val grew = waitUntil { repository.signalCountOnce() > 0 }

        assertTrue(
            "注册了接收者，系统发的亮灭屏广播就应当在 ${WAIT_MILLIS}ms 内落库一条",
            grew,
        )
        // 光「有条数」还不够：万一是别的东西写了库，这条用例就成了假绿。
        // 采集器用的是注入的固定时钟，所以时间戳对上就说明这一条是它写的。
        // 具体是不是亮屏/灭屏，由上面直接调 onBroadcast 的用例守着。
        val stored = repository.signals()
        assertEquals(listOf(now), stored.map { it.at }.distinct())
    }

    /** 让 shell 跑一条命令并把输出读完。不读干净会留下一个挂着的小进程。 */
    private fun shell(command: String) {
        InstrumentationRegistry.getInstrumentation().uiAutomation
            .executeShellCommand(command)
            .use { descriptor ->
                FileInputStream(descriptor.fileDescriptor).use { it.readBytes() }
            }
    }

    private suspend fun waitUntil(condition: suspend () -> Boolean): Boolean {
        val deadline = System.currentTimeMillis() + WAIT_MILLIS
        while (System.currentTimeMillis() < deadline) {
            if (condition()) return true
            delay(POLL_MILLIS)
        }
        return condition()
    }

    private companion object {
        const val WAIT_MILLIS = 10_000L
        const val POLL_MILLIS = 200L
    }
}
