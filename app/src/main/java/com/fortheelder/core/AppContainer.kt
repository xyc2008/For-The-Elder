package com.fortheelder.core

import android.content.Context
import com.fortheelder.core.time.AppClock
import com.fortheelder.core.time.SystemAppClock
import com.fortheelder.data.db.ElderDatabase
import com.fortheelder.data.prefs.SettingsStore
import com.fortheelder.data.prefs.settingsDataStore
import com.fortheelder.data.rhythm.RhythmRepository
import com.fortheelder.domain.engine.greeting.GreetingEngine

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
}
