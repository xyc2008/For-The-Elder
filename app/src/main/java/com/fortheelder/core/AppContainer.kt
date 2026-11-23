package com.fortheelder.core

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import com.fortheelder.core.time.AppClock
import com.fortheelder.core.time.SystemAppClock
import com.fortheelder.data.anchor.AnchorRepository
import com.fortheelder.data.db.ElderDatabase
import com.fortheelder.data.erase.AllDataEraser
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
 *
 * `clock` 是第三个口子（第 31 轮）。上面那句「所有依赖都在这里显式声明，否则引擎就无法在
 * 测试里被替换」一直有个例外：`clock` 本身写死成 [SystemAppClock]，谁都换不掉。JVM 侧的用例
 * 不受影响（它们自己 `new` 引擎、自己传时钟），但**屏幕级用例**是从 `AppContainer` 往下取的，
 * 于是「这会儿该说什么」这一类断言只能按跑测试的钟点碰运气——`SettingsScreenTest` 里那条
 * 安静时段用例就是绕开它写的（往库里摆一个把「现在」裹住的画像，见 ADR-029）。
 * 收下这个参数之后，屏幕级用例可以给一个定死的钟：同一套界面、只差时钟，说的话就该跟着变。
 *
 * 四个口子的默认值都指向应用自己在用的那一份，所以**正式应用的落盘路径与行为一个字节都没动**。
 */
class AppContainer(
    context: Context,
    databaseName: String = ElderDatabase.FILE_NAME,
    settingsData: DataStore<Preferences> = context.applicationContext.settingsDataStore,
    interventionData: DataStore<Preferences> = context.applicationContext.interventionDataStore,
    /**
     * 全应用唯一的「现在」。
     *
     * 所有需要时间的组件都从这一处取（设置层的首次见面日期、讲话节流、作息画像、清理器）。
     * 只有这一处声明，所以替换这一个值就等于把整棵对象图的时间一起挪了。这也是它必须能
     * 注入的原因。
     *
     * 第 32 轮之前这里有两处同名声明：构造参数 `clock`，加类体里一个同名的公开属性。类体那份
     * 的初始化器取的是构造参数，公开属性只是把它抄了一遍，两者恰好指向同一只钟，于是「看不出
     * 差别」纯属巧合——把类体那份接回 [SystemAppClock]，公开属性和内部接线就分了家（第 32 轮
     * 的 M3 变异量出了这件事）。收成构造属性之后，这两者是同一个东西，差异在编译期就不可能存在。
     */
    val clock: AppClock = SystemAppClock,
) {

    private val appContext: Context = context.applicationContext

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
     * 设置页「清空全部数据」的执行者（FR-14 / AC-14.3）。
     *
     * 上面那五样东西（设置、作息、意愿、日常、节流状态）分住在三个存储里，各自的删除入口
     * 一直是有的，但在此之前**没有任何一处把它们放在一起**：设置页那颗按钮只调了
     * `settings.resetToDefaults()`，于是它清不掉另外四样，界面却写着「回到初始状态」。
     * 这一轮把这个缺口补在这里——跨存储的合并属于容器，不属于任何一个仓库，
     * 也不属于 `SettingsStore`（它只该管偏好）。取舍见 `records/03` 的 ADR-066。
     *
     * `by lazy`：上面五个属性都是 `by lazy`，这一条的初始化器要碰它们，提前求值会把
     * Room 的建库和两份 DataStore 一起拖到容器构造的那一刻，等于把冷启动的账算在这里。
     */
    val allDataEraser: AllDataEraser by lazy {
        AllDataEraser(
            clearWishes = { wishRepository.forgetAll() },
            clearAnchors = { anchorRepository.clearAll() },
            clearRhythm = { rhythmRepository.clearAll() },
            resetThrottle = { interventionStore.reset() },
            resetSettings = { settings.resetToDefaults() },
        )
    }

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
