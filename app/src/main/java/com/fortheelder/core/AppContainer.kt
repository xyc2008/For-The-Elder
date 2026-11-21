package com.fortheelder.core

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import com.fortheelder.core.time.AppClock
import com.fortheelder.core.time.SystemAppClock
import com.fortheelder.data.anchor.AnchorRepository
import com.fortheelder.data.db.ElderDatabase
import com.fortheelder.data.intervention.InterventionStore
import com.fortheelder.data.intervention.interventionDataStore
import com.fortheelder.data.prefs.SettingsStore
import com.fortheelder.data.prefs.settingsDataStore
import com.fortheelder.data.rhythm.RhythmCollector
import com.fortheelder.data.rhythm.RhythmMaintenance
import com.fortheelder.data.rhythm.RhythmProfileService
import com.fortheelder.data.rhythm.RhythmRepository
import com.fortheelder.data.wish.WishRepository
import com.fortheelder.domain.engine.greeting.GreetingEngine
import com.fortheelder.domain.engine.intervention.InterventionConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * 手工依赖注入容器。
 *
 * 不引入 Hilt：本项目只有一个 `app` 模块、对象图很浅，Hilt 带来的注解处理开销与
 * 学习成本大于收益。容器由 [com.fortheelder.ElderApp] 创建，生命周期与应用一致。
 *
 * 约定：**所有依赖都在这里显式声明**，界面层不允许自己 `new` 一个引擎或仓库，
 * 否则引擎就无法在测试里被替换。
 *
 * `databaseName` 是给测试留的口子：正式应用永远走默认的 `elder.db`，仪器化测试各传
 * 各的文件名，免得几个用例在同一个进程里争删同一个文件（见 `ElderDatabase.open`）。
 *
 * 后两个参数是同一件事的补齐（第 29 轮）：`settingsData` / `interventionData` 默认就是
 * 应用自己在用的那两份偏好文件，仪器化用例可以传自己的一份进来。补齐之前只有库有口子，
 * 于是五个屏幕级用例读写的都是**这台设备上应用真正的那两份设置**——跑一遍设备测试就会
 * 把用户自己的称呼、字号、节流状态清掉，而且用例的起点还取决于这台机器上恰好留着什么。
 *
 * 偏好这份口子没能像库那样只传一个文件名：`preferencesDataStore` 是委托属性，名字写死在
 * 声明处，参数化不了。所以这里收的是 `DataStore` 实例，默认值取的是那两个委托属性——
 * 正式应用的落盘路径因此一个字节都没动。
 */
class AppContainer(
    context: Context,
    databaseName: String = ElderDatabase.FILE_NAME,
    settingsData: DataStore<Preferences> = context.applicationContext.settingsDataStore,
    interventionData: DataStore<Preferences> = context.applicationContext.interventionDataStore,
) {

    private val appContext: Context = context.applicationContext

    val clock: AppClock = SystemAppClock

    val settings: SettingsStore = SettingsStore(settingsData, clock)

    val greetingEngine: GreetingEngine by lazy { GreetingEngine(clock) }

    /**
     * 本地数据库。`by lazy` 而不是立刻建：Room 的 `databaseBuilder` 第一次真正查询时
     * 才打开文件，提前建出来只会拖长冷启动。
     */
    val database: ElderDatabase by lazy { ElderDatabase.open(appContext, databaseName) }

    val rhythmRepository: RhythmRepository by lazy { RhythmRepository(database, clock) }

    /**
     * 说话的时机（FR-05）。
     *
     * 三件东西分得很开，各管一件事：`interventionConfig` 是一组可调的阈值（纯值对象，没有状态），
     * `interventionStore` 落盘「上次开口/上次被接话/被忽略了几次」，`rhythmProfileService` 决定
     * 「现在这份作息画像是什么」——同一作息日内算过就复用，不再重算。
     *
     * 节流状态走**独立的 DataStore 文件**（`intervention`），不跟设置混用一个：设置页的
     * 「恢复到出厂设置」会 `prefs.clear()`，混在一起会把节流状态一起清掉（见 `interventionDataStore`）。
     */
    val interventionConfig: InterventionConfig = InterventionConfig()

    val interventionStore: InterventionStore by lazy {
        InterventionStore(interventionData, clock)
    }

    /**
     * 作息画像的取用入口。它同时给了 `daily_rhythm` 表真实的读路径和写路径：
     * 在此之前 `saveProfile` / `profile` 在正式代码里没有任何调用者，表只建不用。
     */
    val rhythmProfileService: RhythmProfileService by lazy {
        RhythmProfileService(rhythmRepository, clock)
    }

    /**
     * 被记住的意愿（DR-07）。
     *
     * 跟作息仓库不同，它**没有保留期**：意愿不是靠积累量算出来的统计量，
     * 而是用户自己的事，只由用户决定什么时候忘掉。
     */
    val wishRepository: WishRepository by lazy { WishRepository(database, clock) }

    /**
     * 用户自己记下来的日常（DR-08）。
     *
     * 跟意愿一样没有保留期，也只由用户决定什么时候删。它跟意愿是两回事：意愿是
     * 「想做什么」，锚点是「这一步我什么时候做」。所以两张表分开，删除也各自独立——
     * 用户把一条意愿整个忘掉时，不该连带把他记的日常一起清走。
     */
    val anchorRepository: AnchorRepository by lazy { AnchorRepository(database, clock) }

    /**
     * 过期数据的清理器。
     *
     * 它要判断「今天清过没有」，所以依赖设置层；分界点跟仓库、推断引擎取同一个默认值，
     * 否则「今天」在两边不是同一天，会出现一天清两次。
     */
    val rhythmMaintenance: RhythmMaintenance by lazy {
        RhythmMaintenance(rhythmRepository, settings, clock)
    }

    /**
     * 作息采集器。传的是 application context：接收者的存活时间比任何界面都长，
     * 传 Activity 会漏一个出来。
     */
    val rhythmCollector: RhythmCollector by lazy {
        RhythmCollector(appContext, rhythmRepository, clock, processScope)
    }

    /**
     * 进程级协程域。
     *
     * `SupervisorJob`：清理失败不该把「听设置开关」那条协程一起带走，两件事互不相干。
     * 用 `Dispatchers.Default` 而不是 `Main`，是因为这条线上只有 Room 的写和一次设置读取，
     * 没有一处碰界面。它跟进程同生共死，不做取消。
     */
    private val processScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /**
     * 进程起来之后要一直挂着的事，由 [com.fortheelder.ElderApp.onCreate] 调一次。
     *
     * 两件事：
     * 1. **跟着设置开关走**。用户在设置页改了开关，这里立刻注册或注销接收者。
     *    用 `distinctUntilChanged` 是因为 DataStore 每次写入都会把整个快照重发一遍，
     *    不去重的话改个称呼都会把接收者注销再注册一次。
     * 2. **过期清理**。启动时查一次「今天清过没有」。常伴的进程本来就只在用户打开它的时候
     *    才活着，这一次就够，不需要常驻的后台调度。
     */
    fun startProcessWork() {
        processScope.launch {
            settings.settings
                .map { it.collectRhythm }
                .distinctUntilChanged()
                .collect { enabled -> if (enabled) rhythmCollector.start() else rhythmCollector.stop() }
        }
        processScope.launch { rhythmMaintenance.pruneIfDue() }
    }
}
