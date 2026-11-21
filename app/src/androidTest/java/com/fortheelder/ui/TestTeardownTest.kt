package com.fortheelder.ui

import android.content.Context
import androidx.room.InvalidationTracker
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.fortheelder.data.db.ElderDatabase
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
 * 一处刻意的做法：临时挂上去的那个 Room 观察者，摘掉之后**等 300 ms 再关库**。这不是在用
 * 时间代替事件（`关库前等收尾落地` 才是等事件），而是因为这里造的是一个反常场景——观察者
 * 被摘掉之后，它 `addObserver` 时排下的那次 `syncTriggers` 查询可能还在池子里飞。生产代码与
 * 正式用例里不存在「凭空注册一个观察者再凭空摘掉」，所以这条规则不覆盖它；这一觉只够让那次
 * 查询落地，免得本类自己撞上它要复现的那个崩溃。
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
     */
    @Test
    fun 取消后等收完_收不干净就抛() {
        val 作用域 = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        作用域.launch {
            try {
                awaitCancellation()
            } finally {
                withContext(NonCancellable) { delay(3_000) }
            }
        }
        // 等子任务真的跑进 awaitCancellation 再取消，否则取消可能赶在它启动之前，
        // 那样作用域会立刻收干净，这条用例就变成了一条永远变绿的假用例。
        Thread.sleep(50)
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
        // 见类注释：只有这条用例造得出「观察者被摘掉、它排下的查询还在飞」这个反常状态。
        Thread.sleep(300)
        库.close()
        context.deleteDatabase(临时库名)
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
    }
}
