package com.fortheelder.data.rhythm

import com.fortheelder.core.time.AppClock
import com.fortheelder.domain.engine.rhythm.RhythmConfig
import com.fortheelder.domain.engine.rhythm.RhythmInference
import com.fortheelder.domain.engine.rhythm.RhythmResult
import com.fortheelder.domain.engine.rhythm.rhythmDayOf

/**
 * 「现在这份作息画像是什么」的唯一入口。
 *
 * ## 为什么需要它
 *
 * 推断引擎（[RhythmInference]）本身是无状态的纯函数，仓库（[RhythmRepository]）只管存取。
 * 但「什么时候该重算一遍、算完要不要存」是第三件事，而且必须有人管：
 * 直接把它放在界面里，每个用到画像的地方都会各自调一次推断——一天之内反复算同一批信号，
 * 白烧电量，而且 `daily_rhythm` 表会变成一个只写不读的垃圾场。
 *
 * 所以这里的规则只有一条：**同一个作息日内已经算过，就复用落库的那一份；否则读最近
 * 30 天信号重算，并把结果落库**。它同时给了这张表真实的读路径和写路径。
 *
 * 分界点跟仓库、推断引擎取同一个默认值：三处不一致会让「今天」在两边不是同一天，
 * 于是要么一天重算两次，要么整天都在用昨天的画像。
 */
class RhythmProfileService(
    private val repository: RhythmRepository,
    private val clock: AppClock,
    private val inference: RhythmInference = RhythmInference(),
    private val cutoverHour: Int = RhythmConfig.DEFAULT_CUTOVER_HOUR,
) {

    /**
     * 当前的作息画像。
     *
     * 只有算出来是 [RhythmResult.Ready] 才落库：把「数据不足」「自相矛盾」也存下来，
     * 下次就得先判断存进去的到底是结论还是空响应，白添一层分支。
     */
    suspend fun current(): RhythmResult {
        val today = rhythmDayOf(clock.now(), cutoverHour).toEpochDay()
        repository.profile(today)?.let { return RhythmResult.Ready(it) }

        val result = inference.infer(repository.signals())
        if (result is RhythmResult.Ready) {
            repository.saveProfile(today, result.rhythm)
        }
        return result
    }
}
