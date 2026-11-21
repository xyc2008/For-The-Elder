package com.fortheelder.data.intervention

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.fortheelder.core.time.AppClock
import com.fortheelder.domain.engine.intervention.ThrottleState
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/**
 * 节流状态的落盘位置。
 *
 * **刻意与设置分开一个文件**（`settings` / `intervention`）：设置页的「恢复到出厂设置」
 * 会 `prefs.clear()` 清空整个设置文件，如果节流状态混在里面，用户点一次重置就会顺带
 * 丢掉「上次说到哪儿了」，而对话框里并没有承诺这件事。分开之后，各自的清理范围就是各自的范围。
 */
val Context.interventionDataStore: DataStore<Preferences> by preferencesDataStore(name = "intervention")

/**
 * 「上次开口 / 上次被接话 / 被忽略了几次」的持久化。
 *
 * 只做存取，不做判断：要不要说话由 [com.fortheelder.domain.engine.intervention.InterventionWindow]
 * 现算。这一层要是也插一句判断，同一件事就有两处说法，日后的分歧会很难查。
 *
 * 时刻存成 epoch 毫秒，读回来用 [zone] 还原成 [LocalDateTime]。存 epoch 而不是
 * `LocalDateTime.toString()` 是为了避开格式解析——用户把系统时区改了之后，
 * 存字符串会得到一串「本地时间看起来对、实际含义已经变了」的值。
 */
class InterventionStore(
    private val dataStore: DataStore<Preferences>,
    private val clock: AppClock,
    private val zone: ZoneId = ZoneId.systemDefault(),
) {

    val state: Flow<ThrottleState> = dataStore.data.map(::toThrottle)

    /** 取当前状态一次。判定开场前需要「现在的」状态，用 Flow 反而绕。 */
    suspend fun stateOnce(): ThrottleState = toThrottle(dataStore.data.first())

    /**
     * 记一次开口。
     *
     * 忽略计数在 [ThrottleState.afterSpoken] 里算，并且是在 `edit` 的读改写里算的——
     * 这样即使两次开口的写入撞在一起，也不会各自基于同一份旧状态把计数算成同一个值。
     */
    suspend fun recordSpoken(): ThrottleState = update { it.afterSpoken(clock.now()) }

    /** 记一次接话（用户真的开口了）。计数归零。 */
    suspend fun recordAnswered(): ThrottleState = update { it.afterAnswered(clock.now()) }

    private suspend fun update(transform: (ThrottleState) -> ThrottleState): ThrottleState {
        val updated = dataStore.edit { prefs ->
            val next = transform(toThrottle(prefs))
            // Preferences 对 Long 键只接受非空值，所以「没有这一刻」只能靠删键表达。
            // 正常路径上不会走到 else（记一次开口就一定有时刻），但状态是可以为空的，
            // 写在这里比在调用处假定非空更稳。
            val spoken = next.lastSpokenAt
            if (spoken != null) prefs[KEY_LAST_SPOKEN_AT] = spoken.toEpochMilli()
            else prefs.remove(KEY_LAST_SPOKEN_AT)
            val answered = next.lastAnsweredAt
            if (answered != null) prefs[KEY_LAST_ANSWERED_AT] = answered.toEpochMilli()
            else prefs.remove(KEY_LAST_ANSWERED_AT)
            prefs[KEY_IGNORED_STREAK] = next.ignoredStreak
        }
        return toThrottle(updated)
    }

    private fun toThrottle(prefs: Preferences): ThrottleState = ThrottleState(
        lastSpokenAt = prefs[KEY_LAST_SPOKEN_AT]?.toLocalDateTime(),
        lastAnsweredAt = prefs[KEY_LAST_ANSWERED_AT]?.toLocalDateTime(),
        // 坏数据（负数）在这里就收敛掉，判定层不必再防一次。
        ignoredStreak = (prefs[KEY_IGNORED_STREAK] ?: 0).coerceAtLeast(0),
    )

    private fun LocalDateTime.toEpochMilli(): Long = atZone(zone).toInstant().toEpochMilli()

    private fun Long.toLocalDateTime(): LocalDateTime =
        Instant.ofEpochMilli(this).atZone(zone).toLocalDateTime()

    private companion object {
        val KEY_LAST_SPOKEN_AT = longPreferencesKey("last_spoken_at")
        val KEY_LAST_ANSWERED_AT = longPreferencesKey("last_answered_at")
        val KEY_IGNORED_STREAK = intPreferencesKey("ignored_streak")
    }
}
