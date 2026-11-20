package com.fortheelder.core

import android.content.Context
import com.fortheelder.core.time.AppClock
import com.fortheelder.core.time.SystemAppClock
import com.fortheelder.data.db.ElderDatabase
import com.fortheelder.data.prefs.SettingsStore
import com.fortheelder.data.prefs.settingsDataStore
import com.fortheelder.data.rhythm.RhythmCollector
import com.fortheelder.data.rhythm.RhythmMaintenance
import com.fortheelder.data.rhythm.RhythmRepository
import com.fortheelder.domain.engine.greeting.GreetingEngine
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
 */
class AppContainer(context: Context) {

    private val appContext: Context = context.applicationContext

    val clock: AppClock = SystemAppClock

    val settings: SettingsStore = SettingsStore(appContext.settingsDataStore, clock)

    val greetingEngine: GreetingEngine by lazy { GreetingEngine(clock) }

    /**
     * 本地数据库。`by lazy` 而不是立刻建：Room 的 `databaseBuilder` 第一次真正查询时
     * 才打开文件，提前建出来只会拖长冷启动。
     */
    val database: ElderDatabase by lazy { ElderDatabase.open(appContext) }

    val rhythmRepository: RhythmRepository by lazy { RhythmRepository(database, clock) }

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
