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
import java.util.concurrent.CyclicBarrier
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
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
    /**
     * 落盘文件本身。第 56 轮起对外公开，是为了让用例能**自己读磁盘上的字节**——
     * 「中文称呼经过输入框、校验、落盘之后还完好」这条断言，只读回内存里的
     * `AppSettings` 是证明不了的（那只证明 DataStore 这一层没坏，不证明写出去的字节）。
     *
     * 声明顺序压在 `settingsData` / `interventionData` 前面是必须的：Kotlin 属性按声明
     * 顺序初始化，`PreferenceDataStoreFactory.create { settingsFile }` 里那个 lambda 要
     * 等到第一次读写时才被调用，但既然这里已经把文件建出来了，就先建好再交进去。
     */
    val settingsFile: File = 建个路径(context, 标签, "settings")
    val interventionFile: File = 建个路径(context, 标签, "intervention")

    val settingsData: DataStore<Preferences> = PreferenceDataStoreFactory.create { settingsFile }
    val interventionData: DataStore<Preferences> = PreferenceDataStoreFactory.create { interventionFile }

    private fun 建个路径(context: Context, 标签: String, 后缀: String): File =
        File(context.cacheDir, "$标签-$后缀-${System.nanoTime()}.preferences_pb")
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
 * 等挂在库上的观察者摘完、池子里在飞的活儿也落地，再允许 `database.close()`。
 *
 * 两段，缺一不可。
 *
 * **第一段·数观察者。** 背景（第 34 轮测出来的）：`viewModelStore.clear()` **返回**的那一刻，
 * `InvalidationTracker` 上还挂着观察者；实测要 70~130 ms 才自己归零。为什么是数观察者而不是
 * 「排空查询执行器就完事」：早先连排四轮 `database.queryExecutor` 并每轮等回来，实测无效
 * （5/5 仍是 2 个观察者）。线程栈给出了原因——取消要先派发回 `Dispatchers.Main.immediate`，
 * 之后 `createFlow` 的 `finally` 才把 `removeObserver` 排进池子；在那之前排空，池子里根本
 * 还没有这件事，等回来的自然是一场空。观察者计数才是与「收尾跑没跑」直接对应的不变量，
 * 而且它是确定性的（2 对 0）。
 *
 * **第二段·隔开池子里在飞的活儿。** 第 42 轮的 L3 给出了稳定栈，证明只有第一段还不够：
 * `removeObserver` 先把观察者从表里摘掉（计数当场归零），**之后在同一个任务里**还要走
 * `syncTriggers` → `inTransaction`。归零那一刻这条线程还没放开库，紧接着关库就是
 * `IllegalStateException: attempt to re-open an already-closed object`。
 *
 * 第二段为什么不能是「排 N 个空任务 + `CountDownLatch` 等它们全回来」：那个 latch 只能保证
 * 「有 N 个任务被跑过」，保证不了「每条线程都换过手」。池子是固定线程数的 FIFO 队列，N 条空
 * 任务排进去之后，**一条**空闲线程可以连着跑掉好几条（它跑完一条，队列头还是空的活），于是
 * latch 归零时，那条正跑着尾巴的线程可能一次都没被轮到。（这是交错推理，本轮拿它做了反向
 * 对照才敢这么说，见 `records/04`。）
 *
 * 所以第二段用两阶段屏障：往池子里排**等于池子线程数**的任务，每个任务一进去就等「另外 N-1 条
 * 也上池」。只有 N 条线程**同时**都在跑我们排的活儿，屏障才会开——这等价于「所有线程都已经
 * 放开手里原来的活儿」，正是关库需要的那条不变量。线程数从池子自己读（`maximumPoolSize`），
 * 不写死 4。
 *
 * 找这个池子有个坑，是本轮实测撞出来的：`database.queryExecutor` **不是** `ThreadPoolExecutor`，
 * 而是 `ArchTaskExecutor` 的共享 IO 执行器——一个只实现 `Executor` 的 lambda
 * （`ArchTaskExecutor$$ExternalSyntheticLambda1`）。真正的池子在它背后
 * （`ArchTaskExecutor.sIOThreadExecutor` → `mDelegate` → `DefaultTaskExecutor.mDiskIO`），
 * 要穿过私有字段才够得着。够不着、够到不止一个、够到的不是 `queryExecutor` 派活的那个，都当场
 * 抛，不允许悄悄退化成空转。
 *
 * 两段都等不到就打红，并把还差什么写进消息，而不是让库在未知状态下被关掉。
 */
fun 关库前等收尾落地(database: RoomDatabase, 上限毫秒: Long = 5_000) {
    val 截止 = System.currentTimeMillis() + 上限毫秒
    while (true) {
        val 还剩 = 库里挂着的观察者数(database)
        if (还剩 == 0) break
        if (System.currentTimeMillis() >= 截止) {
            throw AssertionError(
                "关库前等了 $上限毫秒 ms，库里还挂着 $还剩 个观察者：收尾没跑完（见 TestTeardown.kt）",
            )
        }
        Thread.sleep(5)
    }
    val 还剩多少预算 = (截止 - System.currentTimeMillis()).coerceAtLeast(1)
    隔开池子里在飞的活儿(database, 还剩多少预算)
}

/**
 * `关库前等收尾落地` 的「第二段」，机制在这里，理由在那边的注释里。
 *
 * 屏障凑不齐或者任务没能全回来，都不放行：两种情况都意味着池子里还有摸过库的活儿在飞，
 * 此时关库就是把一次偶发崩溃留给下一次。
 */
private fun 隔开池子里在飞的活儿(database: RoomDatabase, 上限毫秒: Long) {
    val 池子 = 找到库在用的池子(database)
    val 线程数 = 池子.maximumPoolSize
    if (线程数 <= 0) {
        throw AssertionError("queryExecutor 背后的池子 maximumPoolSize 是 $线程数：屏障排不出来（见 TestTeardown.kt）")
    }
    if (池子.corePoolSize != 线程数) {
        throw AssertionError(
            "queryExecutor 背后的池子不是固定线程数的（core=${池子.corePoolSize}，max=$线程数）：" +
                "「排 N 条一定有 N 条同时上池」这个前提不成立，屏障得重写（见 TestTeardown.kt）",
        )
    }
    val 一起上池 = CyclicBarrier(线程数)
    val 都回来了 = CountDownLatch(线程数)
    val 没凑齐 = AtomicReference<String?>(null)
    repeat(线程数) { 序号 ->
        database.queryExecutor.execute {
            try {
                一起上池.await(上限毫秒, TimeUnit.MILLISECONDS)
            } catch (错: Throwable) {
                没凑齐.compareAndSet(null, "第 ${序号 + 1} 条任务没能和另外 ${线程数 - 1} 条同时上池：$错")
            } finally {
                都回来了.countDown()
            }
        }
    }
    val 全回来 = try {
        都回来了.await(上限毫秒, TimeUnit.MILLISECONDS)
    } catch (被打断: InterruptedException) {
        Thread.currentThread().interrupt()
        false
    }
    if (!全回来) {
        throw AssertionError(
            "关库前隔开 queryExecutor 等了 $上限毫秒 ms 还没全回来：池子里有活儿没落地（见 TestTeardown.kt）",
        )
    }
    没凑齐.get()?.let { 原因 ->
        throw AssertionError("关库前的屏障没能凑齐：$原因（见 TestTeardown.kt）")
    }
}

/**
 * 库的 `queryExecutor` 到底把活儿派给哪个池子。
 *
 * 第一次尝试是直接用：它自己是 `ThreadPoolExecutor` 就完事。第 43 轮实测走不到这一步——
 * `queryExecutor` 是 `ArchTaskExecutor` 的共享 IO 执行器，一个只实现 `Executor` 的 lambda，
 * 于是穿过 `ArchTaskExecutor.sIOThreadExecutor` →（`mDelegate` / `mDefaultTaskExecutor`）→
 * `DefaultTaskExecutor.mDiskIO` 找到真正的池子。
 *
 * 三处不肯猜：
 * 1. 读出来的共享执行器不是 `queryExecutor` 那个对象就不往下走。`sIOThreadExecutor` 只是
 *    `RoomDatabase.Builder` 的默认值，谁哪天给 `queryExecutor` 装了别的实现，这条链就不成立，
 *    那时该抛，而不是对着一个无辜的池子排屏障。
 * 2. `mDelegate` 与 `mDefaultTaskExecutor` 两条路都读，去重之后**必须只剩一个**池子；剩两个
 *    就说明分不清哪个在给库排活。
 * 3. 字段读不出来（androidx 改了名）或者读出来不是 `ThreadPoolExecutor`，都当场抛。
 */
private fun 找到库在用的池子(database: RoomDatabase): ThreadPoolExecutor {
    val 执行器 = database.queryExecutor
    (执行器 as? ThreadPoolExecutor)?.let { return it }
    val 门面 = try {
        Class.forName("androidx.arch.core.executor.ArchTaskExecutor")
    } catch (没有: ClassNotFoundException) {
        throw AssertionError(
            "queryExecutor 是 ${执行器.javaClass.name}，既不是 ThreadPoolExecutor，也找不到 " +
                "ArchTaskExecutor：不知道它把活儿派给哪个池子（见 TestTeardown.kt）",
            没有,
        )
    }
    val 单例 = 门面.getMethod("getInstance").invoke(null)
        ?: throw AssertionError("ArchTaskExecutor.getInstance() 是 null（见 TestTeardown.kt）")
    val 共享执行器 = 试读私有字段(门面, null, "sIOThreadExecutor")
    if (共享执行器 == null) {
        throw AssertionError(
            "读不出 ArchTaskExecutor.sIOThreadExecutor：androidx 版本变了要重写这段（见 TestTeardown.kt）",
        )
    }
    if (共享执行器 !== 执行器) {
        throw AssertionError(
            "queryExecutor 是 ${执行器.javaClass.name}，而 ArchTaskExecutor 的共享 IO 执行器是 " +
                "${共享执行器.javaClass.name}：不是同一个对象，分不清这个库往哪个池子排活（见 TestTeardown.kt）",
        )
    }
    val 池子 = listOf("mDelegate", "mDefaultTaskExecutor")
        .mapNotNull { 名字 -> 试读私有字段(门面, 单例, 名字) }
        .distinct()
        .mapNotNull { 委托 -> 试读私有字段(委托.javaClass, 委托, "mDiskIO") as? ThreadPoolExecutor }
        .distinct()
    if (池子.size != 1) {
        throw AssertionError(
            "穿过 ArchTaskExecutor 摸到 ${池子.size} 个 IO 池子：要一个刚好的，多了少了都说明这段" +
                "反射跟着 androidx 漂了（见 TestTeardown.kt）",
        )
    }
    return 池子.single()
}

/**
 * 读一个私有字段，读不出来返回 `null`（字段没了、值是 `null`、读不进去，都算）。
 *
 * **不抛**是有意的：调用方拿 `null` 之后必须自己判断，而判断的结果是「凑不齐池子就抛」，
 * 所以「读不出来」不会变成静默放行，只是把原因交给调用方写进消息。
 */
private fun 试读私有字段(持有者: Class<*>, 对象: Any?, 名字: String): Any? {
    val 字段 = try {
        持有者.getDeclaredField(名字)
    } catch (没有: NoSuchFieldException) {
        return null
    }
    return try {
        字段.isAccessible = true
        字段.get(对象)
    } catch (读不动: Throwable) {
        null
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
