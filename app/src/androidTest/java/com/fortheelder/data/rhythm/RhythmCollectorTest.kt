package com.fortheelder.data.rhythm

import android.content.Context
import android.os.SystemClock
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
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
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

    /**
     * 先等在飞的落库协程结束，再关库。
     *
     * 为什么必须等：采集器把写库交给注入的 [scope]，广播路径是「发出即忘」的
     * （[RhythmCollector.onBroadcast] 的返回值只给测试 join 用）。`屏幕开关广播真的能从系统收到`
     * 按两次电源键，系统会投两次广播；用例只要看到「条数 > 0」就返回，**第二次那条写往往还在飞**。
     * 此时 [scope].cancel() 只是让它别再往前走，`database.close()` 却会当场把库关掉；
     * 写库协程随后才走到生成代码的 `finally { endTransaction() }`，Room 会重新打开这个库，
     * 于是在这个线程上「没有当前事务」并抛 IllegalStateException。这条异常发生在没人接的
     * 协程里，会直接打崩仪器化进程——第 25 轮收尾重跑撞上过一次（取证与根因见 `records/04` 第二十四节）。
     *
     * 用 join 而不是「睡一会儿」：睡多久都是猜的，join 是确定的。5 秒是兜底，不是预期时长。
     */
    @After
    fun tearDown() {
        if (collector.isRunning) collector.stop()
        runBlocking {
            withTimeoutOrNull(5_000L) {
                scope.coroutineContext[Job]?.children?.toList()?.forEach { it.join() }
            }
        }
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

        // 预算不写死成一个常数。第 63 轮那条红就是这么来的：同一个方法在健康环境里
        // 0.36~0.45s 就返回（10_000ms 相当于 22~31 倍裕度），却在一个单例要 35~77s 的
        // 环境里顶穿了这 10_000ms。所以先量一下「这台机器现在查一遍条数要多久」，
        // 再按倍数放大预算：环境越慢，允许等的时间越长，判据本身一个字没松。
        val perQueryMillis = measureQueryMillis()
        val budget = budgetOverrideMillis.takeIf { it > 0 } ?: budgetOf(perQueryMillis)
        val pressWindow = pressWindowMillis(perQueryMillis)

        // 截止时刻用单调钟。`System.currentTimeMillis()` 会被对时/时区改动拽走，
        // 拿它做截止，时钟往回跳一次这条用例就假绿了。
        val startedAt = SystemClock.elapsedRealtime()
        var presses = 0
        var injectErrors = 0
        var grew = false
        while (!grew) {
            if (SystemClock.elapsedRealtime() - startedAt >= budget) break
            // 按一下电源键：无论开始时屏幕是亮是灭，这一下都会发生一次亮屏或灭屏。
            // 这一步是真实的系统广播投递——测试代码只按键，不替系统转发。
            presses++
            if (!pressPower()) injectErrors++
            val remain = budget - (SystemClock.elapsedRealtime() - startedAt)
            if (remain <= 0) break
            if (waitUpTo(minOf(pressWindow, remain)) { repository.signalCountOnce() > 0 }) grew = true
        }

        assertTrue(
            "注册了接收者，系统发的亮灭屏广播就应当落库一条。" +
                "预算 ${budget}ms（单次查询 ${perQueryMillis}ms × ${BUDGET_FACTOR}，下限 ${WAIT_BASE_MILLIS}ms" +
                (if (budgetOverrideMillis > 0) "，本次被 -e rhyBudgetMillis=${budgetOverrideMillis} 覆盖" else "") +
                "）；实等 ${SystemClock.elapsedRealtime() - startedAt}ms；按键 ${presses} 次" +
                "（其中 ${injectErrors} 次注射报错）；库中条数 ${repository.signalCountOnce()}",
            grew,
        )
        // 光「有条数」还不够：万一是别的东西写了库，这条用例就成了假绿。
        // 采集器用的是注入的固定时钟，所以时间戳对上就说明这一条是它写的。
        // 具体是不是亮屏/灭屏，由上面直接调 onBroadcast 的用例守着。
        val stored = repository.signals()
        assertEquals(listOf(now), stored.map { it.at }.distinct())
    }

    /**
     * 负向对照用：`-e rhyBudgetMillis 1` 把预算压到 1ms。
     *
     * 留着它是因为「改一条测试把红改绿」最怕的就是把用例改成永远绿。有这么一条命令行，
     * 谁都能让这条用例当场红一次，看报文里究竟写了什么，而不用改回代码再改回来。
     */
    private val budgetOverrideMillis: Long =
        InstrumentationRegistry.getArguments().getString("rhyBudgetMillis")?.toLongOrNull() ?: -1L

    /**
     * 量一次「这台机器现在查一遍条数」要多久，取多次平均，单位毫秒。
     *
     * 先热身三遍：首次查询要建连、加载类，那是**一次性**开销，算进平均会把预算抬得没边。
     */
    private suspend fun measureQueryMillis(): Long {
        repeat(3) { repository.signalCountOnce() }
        val startedAt = SystemClock.elapsedRealtime()
        repeat(QUERY_SAMPLES) { repository.signalCountOnce() }
        return maxOf(1L, (SystemClock.elapsedRealtime() - startedAt) / QUERY_SAMPLES)
    }

    /** 预算 = max(下限, 单次查询耗时 × 倍数)。 */
    private fun budgetOf(perQueryMillis: Long): Long =
        maxOf(WAIT_BASE_MILLIS, perQueryMillis * BUDGET_FACTOR)

    /** 一次「按一下、再找一遍」里愿意等的时长。 */
    private fun pressWindowMillis(perQueryMillis: Long): Long =
        (perQueryMillis * PRESS_WINDOW_FACTOR).coerceIn(MIN_PRESS_WINDOW_MILLIS, WAIT_BASE_MILLIS)

    /** 让 shell 跑一条命令，把输出读完并返回。不读干净会留下一个挂着的小进程。 */
    private fun shell(command: String): String =
        InstrumentationRegistry.getInstrumentation().uiAutomation
            .executeShellCommand(command)
            .use { descriptor ->
                FileInputStream(descriptor.fileDescriptor).use { String(it.readBytes(), Charsets.UTF_8) }
            }

    /** 按一次电源键；shell 报错就返回 false——「注射失败」和「广播没到」要分得开。 */
    private fun pressPower(): Boolean =
        INJECT_ERROR_MARKERS.none { shell("input keyevent 26").contains(it) }

    private suspend fun waitUpTo(limitMillis: Long, condition: suspend () -> Boolean): Boolean {
        val deadline = SystemClock.elapsedRealtime() + limitMillis
        while (SystemClock.elapsedRealtime() < deadline) {
            if (condition()) return true
            delay(POLL_MILLIS)
        }
        return condition()
    }

    private companion object {
        /** 下限：健康环境下这条用例本来 0.3~0.5s 就返回。 */
        const val WAIT_BASE_MILLIS = 10_000L

        /** 放大倍数：单次查询 100ms 的塌速环境 → 40s 预算。 */
        const val BUDGET_FACTOR = 400L

        /** 单次「按一下再找一遍」的窗口：同样按实测速度放大，但不小于 0.5s、不大于下限。 */
        const val PRESS_WINDOW_FACTOR = 50L
        const val MIN_PRESS_WINDOW_MILLIS = 500L

        const val QUERY_SAMPLES = 20
        const val POLL_MILLIS = 200L
        val INJECT_ERROR_MARKERS = listOf("Error", "Exception")
    }
}
