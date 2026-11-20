package com.fortheelder.data.rhythm

import com.fortheelder.core.time.AppClock
import com.fortheelder.data.prefs.SettingsStore
import com.fortheelder.domain.engine.rhythm.RhythmConfig
import com.fortheelder.domain.engine.rhythm.rhythmDayOf

/**
 * 过期数据的定期清理。
 *
 * **不引入 WorkManager**。第 4 轮曾把 `androidx-work-runtime-ktx` 声明在依赖里却零引用，
 * 第 5 轮已删掉；现在为了「每天清一次」再把它加回来不划算——清理只是两条 `DELETE`，
 * 挂在进程启动时跑一次就够，因为常伴的进程本来就只在用户打开它的时候才活着。
 *
 * 「一天最多一次」记在设置里（[SettingsStore.lastPruneEpochDay]）而不是内存里：
 * 用户一天里可能开关常伴十几次，每次都扫一遍全表没有意义。而这个日期不是用户设置，
 * 所以它不进 `AppSettings`，界面看不到它。
 *
 * 清理由仓库层的 [RhythmRepository.prune] 真正执行，这里只负责「该不该清」这一判断；
 * 两者的分界是：仓库管数据窗口的算法（哪些算过期），这里管运行节奏（什么时候查一次）。
 *
 * @param cutoverHour 作息日分界点，必须和仓库、推断引擎用的是同一个值，
 *   否则「今天是否已经清过」会按另一个分界点翻篇，出现一天清两次或一天都不清。
 */
class RhythmMaintenance(
    private val repository: RhythmRepository,
    private val settings: SettingsStore,
    private val clock: AppClock,
    private val cutoverHour: Int = RhythmConfig.DEFAULT_CUTOVER_HOUR,
) {

    /**
     * 今天还没清过就清一次，返回删掉的行数；今天已经清过就返回 0。
     *
     * 返回的是**真正删掉的行数**，测试用它区分「清了但没什么可删」和「今天不打算清」——
     * 两者都返回 0 会让用例写不出有效断言。
     */
    suspend fun pruneIfDue(): Int {
        val today = rhythmDayOf(clock.now(), cutoverHour).toEpochDay()
        if (settings.lastPruneEpochDay() == today) return 0
        val removed = repository.prune()
        settings.markPruned(today)
        return removed
    }
}
