package com.fortheelder.ui

import android.content.Context
import androidx.room.InvalidationTracker
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.fortheelder.data.db.ElderDatabase
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * `TestTeardown.kt` 里两个「等」——红绿两侧各验一次。
 *
 * 为什么要专门有一类：这两个助手本身是**测试的脚手架**，它们写错的方式恰好是最危险的那种
 * ——静默通过。等一个永远等于 0 的量、或者 `join()` 一个早就结束的 Job，都会让所有用例照常
 * 变绿，而缺陷照旧偶发。所以这里各造一个「一定等不到」的场景，要求它当场报错。
 *
 * 三个用例都不挂 compose 规则，也就都不经过 `关库排在组合之后`：本类验的正是那条规则里
 * 用的两个零件，套上规则会把「零件坏了」和「规则坏了」混在一起。
 *
 * 一处清账：本类原先在「凭空注册一个观察者再凭空摘掉」那条用例的末尾睡 300 ms，为的是让摘掉
 * 之后还留在池子里的那次 `syncTriggers` 查询落地——那时 `关库前等收尾落地` 只数观察者，够不着
 * 这种尾巴，只能靠睡。第 43 轮给它补上了第二段（隔开池子里在飞的活儿），尾巴改由助手负责，
 * 那一觉就删掉了。
 */
@RunWith(AndroidJUnit4::class)
class TestTeardownTest {

    private val context: Context
        get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun 取消后等收完_收干净了就放行() {
        val 作用域 = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        作用域.launch { delay(20) }
        作用域.cancel()
        取消后等收完(作用域, 上限毫秒 = 5_000)
    }

    /**
     * 子任务在 `finally` 里挂着 `NonCancellable` 的活儿，取消之后作用域**不会**立刻收干净。
     * 这时 `取消后等收完` 必须抛 `AssertionError`，而不是默默返回。
     *
     * 上限给 100 ms：子任务要磨 3 s，两者的差距足够大，不靠碰运气。
     *
     * 第 63 轮修一处：原先靠 `Thread.sleep(50)` 等子任务起来，那条护栏在 L3 全量复跑里输过一次
     * —— 子任务还排在 `Dispatchers.Default` 队列里就被取消，`try/finally` 没进，那 3 s 从没开始磨，
     * 作用域当场收干净，助手于是静默返回、`抓到` 是 null（单例耗时 0.134 s）。
     * 那次不是「绿了」，是**因为错误的原因报红**。改成起点屏障：子任务体第一行 `countDown()`，
     * 主线程等到它才取消。这样无论取消落在 `countDown` 与 `awaitCancellation` 之间哪一处，
     * `finally` 都会跑，助手必然超时报错。
     */
    @Test
    fun 取消后等收完_收不干净就抛() {
        val 作用域 = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val 起来了 = CountDownLatch(1)
        作用域.launch {
            try {
                起来了.countDown()
                awaitCancellation()
            } finally {
                withContext(NonCancellable) { delay(3_000) }
            }
        }
        assertTrue("子任务 2 s 内没起来，这条用例问不了它要问的事", 起来了.await(2, TimeUnit.SECONDS))
        作用域.cancel()
        val 抓到 = 抓错 { 取消后等收完(作用域, 上限毫秒 = 100) }
        assertTrue(
            "作用域 3 s 内收不完，期望抛 AssertionError，实际是 ${抓到?.javaClass?.name}",
            抓到 is AssertionError,
        )
    }

    /**
     * `关库前等收尾落地` 的两个分支：库上还挂着观察者时报错，摘下之后放行。
     *
     * 观察者是手工挂的，所以「还有几个」完全可控——这正是这条用例能确定性地打红的原因。
     * 生产者（真页面）那一侧的观察者数是 2，但那个数字是实现的细节，助手只该等 0，不该写死。
     */
    @Test
    fun 关库前等收尾落地_还挂着观察者就抛_摘干净就放行() {
        val 库 = ElderDatabase.open(context, 临时库名)
        val 观察者 = object : InvalidationTracker.Observer("wish") {
            override fun onInvalidated(tables: Set<String>) = Unit
        }
        库.invalidationTracker.addObserver(观察者)
        val 抓到 = 抓错 { 关库前等收尾落地(库, 上限毫秒 = 200) }
        assertTrue(
            "库上挂着 1 个观察者，期望抛 AssertionError，实际是 ${抓到?.javaClass?.name}",
            抓到 is AssertionError,
        )

        库.invalidationTracker.removeObserver(观察者)
        关库前等收尾落地(库)
        库.close()
        context.deleteDatabase(临时库名)
    }

    /**
     * 观察者归零**不等于**可以关库：摘观察者的那条线程接下来还要往下走。
     *
     * 第 42 轮的 L3 给出了稳定栈：`removeObserver` 之后紧接着 `syncTriggers` → `inTransaction`，
     * 崩溃是 `attempt to re-open an already-closed object`。也就是说计数归零那一刻，库上确实
     * 已经没人挂了，但**那条线程还没放开库**。这条用例把那个尾巴做成可控的 300 ms：
     * 摘掉观察者（计数当场归零）之后在同一个任务里磨够时间，要求助手等它落地。
     *
     * 旧实现只看计数，会立刻放行，于是耗时接近 0 —— 这条用例就是它的红。
     */
    @Test
    fun 关库前等收尾落地_尾巴还在飞的时候不放行() {
        val 库 = ElderDatabase.open(context, 尾巴库名)
        val 观察者 = object : InvalidationTracker.Observer("wish") {
            override fun onInvalidated(tables: Set<String>) = Unit
        }
        库.invalidationTracker.addObserver(观察者)

        val 尾巴开始了 = CountDownLatch(1)
        库.queryExecutor.execute {
            // 这就是 `removeObserver` 的真实形状：先把观察者从表里摘掉（计数当场归零），
            // 再在**同一个任务**里往下跑 `syncTriggers`。归零与「这条线程放开库」隔着这一段。
            库.invalidationTracker.removeObserver(观察者)
            尾巴开始了.countDown()
            Thread.sleep(300)
        }
        assertTrue("尾巴没能在 5 s 内开始", 尾巴开始了.await(5, TimeUnit.SECONDS))

        var 放行时尾巴已经落地 = false
        try {
            val 起 = System.nanoTime()
            关库前等收尾落地(库)
            val 耗时毫秒 = (System.nanoTime() - 起) / 1_000_000
            放行时尾巴已经落地 = 耗时毫秒 >= 300
            assertTrue("尾巴还在飞就放行了：只等了 $耗时毫秒 ms，而它要磨 300 ms", 放行时尾巴已经落地)
        } finally {
            // 红例里尾巴确实还在飞，这时候直接关库会把这个红例盖成一条跟被测行为无关的崩溃。
            if (!放行时尾巴已经落地) Thread.sleep(400)
            库.close()
            context.deleteDatabase(尾巴库名)
        }
    }

    private fun 抓错(块: () -> Unit): Throwable? =
        try {
            块()
            null
        } catch (错: Throwable) {
            错
        }

    private companion object {
        /**
         * 跟屏幕级用例分开的名字：本类不跟它们并行跑，但同一个进程里几个用例争删同一个文件
         * 是 `ElderDatabase.open` 那里明确要避免的事（见 `AppContainer` 的 `databaseName` 口子）。
         */
        const val 临时库名 = "elder-test-teardown.db"

        /** 「尾巴还在飞」那条用例自己的库名，理由同上：几者别争同一个文件。 */
        const val 尾巴库名 = "elder-test-teardown-tail.db"
    }
}
