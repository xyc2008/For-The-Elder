package com.fortheelder.data.intervention

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.fortheelder.core.time.AppClock
import com.fortheelder.data.datastore.guardFirstReadRace
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
 * **刻意与设置分开一个文件**（`settings` / `intervention`）：两个文件各自的清理范围就是各自的
 * 范围。第 71 轮起设置页的「清空全部数据」两样都会清，但那是在
 * [com.fortheelder.data.erase.AllDataEraser] 里**逐项写明**的一步（`interventionStore.reset()`），
 * 不是靠共用一个文件顺带扫掉的。两个文件混在一起的话，往后任何一条只清设置的路径都会不声不响
 * 地把节流状态一起带走——而对话框里那句话只承诺「全部数据」这一件事，它不该替别的路径背书。
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

    val state: Flow<ThrottleState> = dataStore.data.guardFirstReadRace().map(::toThrottle)

    /** 取当前状态一次。判定开场前需要「现在的」状态，用 Flow 反而绕。 */
    suspend fun stateOnce(): ThrottleState = toThrottle(dataStore.data.guardFirstReadRace().first())

    /**
     * 记一次开口。
     *
     * 忽略计数在 [ThrottleState.afterSpoken] 里算，并且是在 `edit` 的读改写里算的——
     * 这样即使两次开口的写入撞在一起，也不会各自基于同一份旧状态把计数算成同一个值。
     */
    suspend fun recordSpoken(): ThrottleState = update { it.afterSpoken(clock.now()) }

    /** 记一次接话（用户真的开口了）。计数归零。 */
    suspend fun recordAnswered(): ThrottleState = update { it.afterAnswered(clock.now()) }

    /**
     * 清回「从来没说过话」。
     *
     * 只有设置页的「清空全部数据」这一条路径会调它（FR-14 / AC-14.3）。用 `prefs.clear()`
     * 整文件清，而不是逐个 `remove` 三个键：逐个删的话，日后在这里添了一个新键却忘了补一行，
     * 就会出现「清空之后还剩一点过去」的残渣——那种漏法在界面上看不出来，用户也无从发现。
     *
     * **它刻意不属于** [com.fortheelder.data.prefs.SettingsStore.resetToDefaults]（见本文件
     * 顶部关于两个文件分开的说明）：设置那一层只管设置，把节流状态并进去就等于让「只清设置」
     * 这条路径偷偷多清一样东西。跨存储的合并发生在
     * [com.fortheelder.data.erase.AllDataEraser]，那才是「清空全部」这一件事的家。
     */
    suspend fun reset() {
        dataStore.edit { prefs -> prefs.clear() }
    }

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
