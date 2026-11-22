package com.fortheelder.data.prefs

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.fortheelder.core.time.AppClock
import com.fortheelder.core.time.toEpochDay
import com.fortheelder.data.datastore.guardFirstReadRace
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/**
 * 界面字号档位。
 *
 * 适老应用最实际的一个开关就是这个：很多 55 岁以上的用户已经把系统字号调到最大，
 * 但仍有相当一部分人不愿意动系统设置，只在单个应用里希望字再大一点。
 * 这里的选择是**应用内独立字号**，叠加在系统字号之上而不是覆盖它
 * （见 `ui/theme/Type.kt`）。注意平台对系统字号做非线性换算，档位区分度会随系统字号升高而下降，见 ADR-004。
 */
enum class FontSize(val storageKey: String, val label: String, val scale: Float) {
    STANDARD("standard", "标准", 1.0f),
    LARGE("large", "大一点", 1.15f),
    EXTRA("extra", "更大", 1.30f),
    ;

    companion object {
        fun fromKey(key: String?): FontSize = entries.firstOrNull { it.storageKey == key } ?: STANDARD
    }
}

/** 应用的全部持久化设置。这里是唯一的真相来源，界面只读它，不各自缓存。 */
data class AppSettings(
    /** 用户希望被怎么称呼。null 表示还没填写，此时问候语不带称呼。 */
    val address: String? = null,
    val fontSize: FontSize = FontSize.STANDARD,
    /** 首次启动当天（epoch day）。null 表示还没落库。 */
    val firstLaunchEpochDay: Long? = null,
    /** 引导流程是否走完。false 时应用只显示引导页。 */
    val onboardingDone: Boolean = false,
    /**
     * 用户是否同意常伴采集手机作息信号（AC-04.4）。
     *
     * **默认 false：没问过用户之前不采。** 这不是技术上的稳妥，而是这类数据天生属于
     * 「要开口问一次」的东西——做成默认开启、再让用户自己去设置里找开关关掉，
     * 等于拿用户的没看到当作同意。
     *
     * false 时应用功能完整：问候、称呼、字号、设置全部照常，只是没有作息画像可用。
     */
    val collectRhythm: Boolean = false,
    /**
     * 用户明确要求「今天先安静一会儿」的那一天（epoch day）。null 表示没要求过（AC-05.4）。
     *
     * 存的是**哪一天**，不是「要不要安静」这个布尔值：这句话的效力只到当天为止，
     * 到期自动失效因此是数据结构的必然结果，不需要任何计时器或后台任务去关它（ADR-028）。
     * 代价是用户改系统日期会让它提前或延后失效，这一点接受。
     */
    val quietEpochDay: Long? = null,
)

/**
 * 设置的读写。
 *
 * 刻意不做 `SettingsRepository` 接口 + 实现的分层：这一层没有多套实现的需要，
 * 加一层接口只会让调用链更长。真正需要替换的是它下面的 [DataStore]。
 */
class SettingsStore(
    private val dataStore: DataStore<Preferences>,
    private val clock: AppClock,
) {
    val settings: Flow<AppSettings> = dataStore.data.guardFirstReadRace().map { prefs ->
        AppSettings(
            address = prefs[KEY_ADDRESS]?.takeIf { it.isNotBlank() },
            fontSize = FontSize.fromKey(prefs[KEY_FONT_SIZE]),
            firstLaunchEpochDay = prefs[KEY_FIRST_LAUNCH_DAY],
            onboardingDone = prefs[KEY_ONBOARDING_DONE] ?: false,
            collectRhythm = prefs[KEY_COLLECT_RHYTHM] ?: false,
            quietEpochDay = prefs[KEY_QUIET_DAY],
        )
    }

    /**
     * 首次启动时把当天记下来，之后不再改动。
     *
     * 写成「没有才写」而不是「每次都写」，否则每次启动都会把首次日期冲掉，
     * 「认识多少天了」就永远显示第 1 天。
     */
    suspend fun ensureFirstLaunchRecorded() {
        val today = clock.now().toEpochDay()
        dataStore.edit { prefs ->
            if (prefs[KEY_FIRST_LAUNCH_DAY] == null) {
                prefs[KEY_FIRST_LAUNCH_DAY] = today
            }
        }
    }

    suspend fun setAddress(value: String?) {
        val normalized = value?.trim()?.takeIf { it.isNotEmpty() }
        dataStore.edit { prefs ->
            if (normalized == null) prefs.remove(KEY_ADDRESS) else prefs[KEY_ADDRESS] = normalized
        }
    }

    suspend fun setFontSize(value: FontSize) {
        dataStore.edit { prefs -> prefs[KEY_FONT_SIZE] = value.storageKey }
    }

    suspend fun completeOnboarding() {
        dataStore.edit { prefs -> prefs[KEY_ONBOARDING_DONE] = true }
    }

    /**
     * 用户改变主意时用的开关（AC-04.4）。
     *
     * 撤回同意**不删已经记下的数据**：那是两件事，删数据在设置页有单独的按钮、单独问一次。
     * 这里只管「以后还记不记」。改动由 [com.fortheelder.core.AppContainer] 监听到之后
     * 立刻注册或注销广播接收者，不需要重启应用。
     */
    suspend fun setCollectRhythm(value: Boolean) {
        dataStore.edit { prefs -> prefs[KEY_COLLECT_RHYTHM] = value }
    }

    /**
     * 上次清理过期作息数据是哪一天（作息日）。null 表示这台手机上还没清过。
     *
     * 它不是用户设置，所以**不进 [AppSettings]**：界面上看不到它，用户也不该能改它。
     * 什么时候查一次由 [com.fortheelder.data.rhythm.RhythmMaintenance] 决定。
     */
    suspend fun lastPruneEpochDay(): Long? =
        dataStore.data.guardFirstReadRace().first()[KEY_LAST_PRUNE_DAY]

    suspend fun markPruned(epochDay: Long) {
        dataStore.edit { prefs -> prefs[KEY_LAST_PRUNE_DAY] = epochDay }
    }

    /**
     * 记下用户要求「今天先安静一会儿」（AC-05.4）。
     *
     * 只存日期，不存布尔值：这句话的有效期就是这一天，跨过午夜自动失效是数据本身的结果，
     * 不靠定时器把它关掉（ADR-028）。
     */
    suspend fun setQuietOn(epochDay: Long) {
        dataStore.edit { prefs -> prefs[KEY_QUIET_DAY] = epochDay }
    }

    /**
     * 用户改主意了，现在就可以说话。
     *
     * 删除而不是写入「不是今天」：没要求过安静是默认状态，不留下一个容易被误读的痕迹。
     */
    suspend fun clearQuiet() {
        dataStore.edit { prefs -> prefs.remove(KEY_QUIET_DAY) }
    }

    /** 把设置恢复到出厂状态。引导状态会被重置，重新打开应用会再次看到引导页。 */
    suspend fun resetToDefaults() {
        dataStore.edit { prefs -> prefs.clear() }
    }

    private companion object {
        val KEY_ADDRESS = stringPreferencesKey("address")
        val KEY_FONT_SIZE = stringPreferencesKey("font_size")
        val KEY_FIRST_LAUNCH_DAY = longPreferencesKey("first_launch_day")
        val KEY_ONBOARDING_DONE = booleanPreferencesKey("onboarding_done")
        val KEY_COLLECT_RHYTHM = booleanPreferencesKey("collect_rhythm")
        val KEY_LAST_PRUNE_DAY = longPreferencesKey("last_prune_day")
        val KEY_QUIET_DAY = longPreferencesKey("quiet_day")
    }
}
