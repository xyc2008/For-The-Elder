package com.fortheelder.ui

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.room.RoomDatabase
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import com.fortheelder.core.AppContainer
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertTrue
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runners.model.Statement

/**
 * 「关掉测试库」这一步的收尾规则。用法是**套在 compose 规则的外面**：
 *
 * ```
 * private val compose = createComposeRule()
 *
 * @get:Rule
 * val rules: RuleChain = RuleChain.outerRule(关库排在组合之后(TEST_DB) { container }).around(compose)
 * ```
 *
 * `RuleChain` 的收尾是内层先跑，于是宿主 Activity 先被 compose 规则销毁
 * （组合销毁 → `ViewModelStore.clear()` → `viewModelScope` 取消），之后才轮到这里关库。
 * 顺序反过来会留下一个窗口：页面上的收集器还挂在 `viewModelScope` 上，库却已经关了。
 *
 * 为什么屏幕级用例需要这条规则、而视图模型用例不需要：`TalkViewModelTest` /
 * `HomeViewModelTest` 里的 `ViewModelStore` 是手工建的，它们自己写 `viewModelStore.clear()`
 * 并且**写在 `database.close()` 之前**；屏幕级用例的视图模型活在 compose 规则自己的 store
 * 里，外面够不着，只能靠收尾顺序把这一步交回给规则。
 *
 * 关库之前先确认宿主 Activity 真的已经不在前台了。这条不变量被谁改没了，症状是「偶发」
 * 而不是「必红」——与其靠注释提醒，不如让它当场断言。
 */
fun 关库排在组合之后(databaseName: String, container: () -> AppContainer): TestRule =
    object : TestRule {
        override fun apply(base: Statement, description: Description): Statement =
            object : Statement() {
                override fun evaluate() {
                    try {
                        base.evaluate()
                    } finally {
                        要求宿主已经不在前台()
                        关库前等收尾落地(container().database)
                        container().database.close()
                        InstrumentationRegistry.getInstrumentation().targetContext
                            .deleteDatabase(databaseName)
                    }
                }
            }
    }

/**
 * 一个屏幕级用例自己的两份 DataStore（设置、节流状态）。用法：
 *
 * ```
 * private lateinit var 存储: 自己的存储
 *
 * @Before fun 摆好() {
 *     存储 = 自己的存储(context, TEST_DB)
 *     container = AppContainer(context, TEST_DB, 存储.settingsData, 存储.interventionData)
 * }
 * ```
 *
 * 为什么要自己一份：`AppContainer` 后两个参数的默认值就是应用正在用的那两个委托属性，
 * 用例不传就是在写**这台设备上应用真正的设置**（第 28 轮的 `SettingsScreenTest` 收尾里
 * 那句 `resetToDefaults()` 就是这么干的，跑一遍设备测试会把用户自己的称呼清掉）。
 *
 * 三处刻意为之：
 * 1. **两个文件分开**（`-settings` / `-intervention`），跟生产一致。混成一个的话，
 *    「清空设置」会把节流状态一起清掉，正好毁掉被测的那条语义。
 * 2. 文件名带 `nanoTime` 落在 `cacheDir`，所以每例一份、起点天然干净（本来就是空的，
 *    不需要先 `resetToDefaults()`），也不需要事后删——设备测试本来就跑在一次性环境里。
 * 3. 作用域用 `PreferenceDataStoreFactory.create` 的默认值，**不在 `@After` 里取消**：
 *    `@After` 跑在 compose 规则收摊之前，那一刻页面上的收集器还挂在 DataStore 上，
 *    取消它的作用域会把异常打进没人接的协程里（`HomeViewModelTest` 里有同类警告）。
 */
class 自己的存储(context: Context, 标签: String) {
    val settingsData: DataStore<Preferences> = 建一份(context, "$标签-settings")
    val interventionData: DataStore<Preferences> = 建一份(context, "$标签-intervention")

    private fun 建一份(context: Context, 名字: String): DataStore<Preferences> =
        PreferenceDataStoreFactory.create {
            File(context.cacheDir, "$名字-${System.nanoTime()}.preferences_pb")
        }
}

/**
 * 前台上一个 Activity 都不剩。
 *
 * `ActivityLifecycleMonitorRegistry` 要求在主线程上问，所以这里过一遍 `runOnMainSync`。
 * 返回的名单里还有东西就说明 compose 规则还没把宿主收掉，那意味着收尾顺序被调回去了。
 */
private fun 要求宿主已经不在前台() {
    var 前台的宿主数 = -1
    InstrumentationRegistry.getInstrumentation().runOnMainSync {
        前台的宿主数 = ActivityLifecycleMonitorRegistry.getInstance()
            .getActivitiesInStage(Stage.RESUMED)
            .size
    }
    assertTrue(
        "关库时前台上还有 $前台的宿主数 个 Activity：收尾顺序反了（见 TestTeardown.kt）",
        前台的宿主数 == 0,
    )
}

/**
 * 数一数 Room 的 `InvalidationTracker` 上还挂着几个观察者。
 *
 * 只能靠反射：`observerMap` 没有公开读法；字段类型是 `androidx.arch.core.internal.SafeIterableMap`
 * 而**不是** `Map`，强转会 `ClassCastException`，所以只能问它的 `size()`。
 *
 * 拿不到字段就当场抛：Room 换了字段名要重写这段，不能悄悄退化成「永远返回 0」——
 * 那会让下面那个等待变成彻头彻尾的空转。
 */
private fun 库里挂着的观察者数(database: RoomDatabase): Int {
    val 追踪器 = database.invalidationTracker
    val 字段 = try {
        追踪器.javaClass.getDeclaredField("observerMap")
    } catch (没有: NoSuchFieldException) {
        throw AssertionError(
            "InvalidationTracker 上找不到 observerMap 字段，Room 版本变了要重写这段（见 TestTeardown.kt）",
            没有,
        )
    }
    字段.isAccessible = true
    val 表 = 字段.get(追踪器) ?: return 0
    return 表.javaClass.getMethod("size").invoke(表) as Int
}

/**
 * 等挂在库上的观察者真的摘完，再允许 `database.close()`。
 *
 * 背景（第 34 轮测出来的）：`viewModelStore.clear()` **返回**的那一刻，`InvalidationTracker`
 * 上还挂着观察者；实测要 70~130 ms 才自己归零。清空之后立刻关库就落在这个窗口里，崩溃是
 * `IllegalStateException: attempt to re-open an already-closed object: SQLiteDatabase`，
 * 栈是 `SQLiteDatabase.inTransaction` ← `InvalidationTracker.syncTriggers` ← `removeObserver`。
 *
 * 为什么是「等观察者归零」而不是「排空查询执行器」：先写的正是后者——连着四轮往
 * `database.queryExecutor` 排空任务并每轮等回来。实测无效（5/5 仍是 2 个观察者）。线程栈给出了
 * 原因：`ElderDatabase.open` 没给 Room 自定义 executor，`queryExecutor` 就是 ArchTaskExecutor
 * 的共享 IO 池（`arch_disk_io_0..3`，固定四线程），取消那一刻两条收集器正**占着其中两条线程**
 * 跑首次发射的阻塞查询（`AnchorDao` / `WishDao`），摘观察者排在它们后面，而我排的空任务落在
 * **空闲**线程上——屏障原理上就够不着那两条在飞的任务，不是调参能救的。
 * 观察者计数才是与「能不能关库」直接对应的不变量，而且它是确定性的（2 对 0）。
 *
 * 等不到就打红，并把还剩几个写进消息，而不是让库在未知状态下被关掉。
 */
fun 关库前等收尾落地(database: RoomDatabase, 上限毫秒: Long = 5_000) {
    val 截止 = System.currentTimeMillis() + 上限毫秒
    while (true) {
        val 还剩 = 库里挂着的观察者数(database)
        if (还剩 == 0) return
        if (System.currentTimeMillis() >= 截止) {
            throw AssertionError(
                "关库前等了 $上限毫秒 ms，库里还挂着 $还剩 个观察者：收尾没跑完（见 TestTeardown.kt）",
            )
        }
        Thread.sleep(5)
    }
}

/**
 * 等一个已取消的作用域收干净，再去做删文件之类只能做一次的事。
 *
 * 治的是 `HomeViewModelTest` / `TalkViewModelTest` 收尾里那条
 * `java.io.FileNotFoundException: .../cache/talk-vm-*.preferences_pb: open failed: ENOENT`
 * ——它是 `Dispatchers.IO` 上的 DataStore 读在文件被删掉之后才跑到。
 *
 * 作用是空的时候直接返回：没必要为「没有 Job 的作用域」断言。
 */
fun 取消后等收完(scope: CoroutineScope, 上限毫秒: Long = 5_000) {
    val 任务 = scope.coroutineContext[Job] ?: return
    try {
        runBlocking {
            withTimeout(上限毫秒) { 任务.join() }
        }
    } catch (超时: TimeoutCancellationException) {
        throw AssertionError("作用域取消后 $上限毫秒 ms 仍没收完（见 TestTeardown.kt）", 超时)
    }
}
