package com.fortheelder.data.prefs

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.fortheelder.core.time.AppClock
import com.fortheelder.core.time.toEpochDay
import kotlinx.coroutines.flow.Flow
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
    val settings: Flow<AppSettings> = dataStore.data.map { prefs ->
        AppSettings(
            address = prefs[KEY_ADDRESS]?.takeIf { it.isNotBlank() },
            fontSize = FontSize.fromKey(prefs[KEY_FONT_SIZE]),
            firstLaunchEpochDay = prefs[KEY_FIRST_LAUNCH_DAY],
            onboardingDone = prefs[KEY_ONBOARDING_DONE] ?: false,
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

    /** 把设置恢复到出厂状态。引导状态会被重置，重新打开应用会再次看到引导页。 */
    suspend fun resetToDefaults() {
        dataStore.edit { prefs -> prefs.clear() }
    }

    private companion object {
        val KEY_ADDRESS = stringPreferencesKey("address")
        val KEY_FONT_SIZE = stringPreferencesKey("font_size")
        val KEY_FIRST_LAUNCH_DAY = longPreferencesKey("first_launch_day")
        val KEY_ONBOARDING_DONE = booleanPreferencesKey("onboarding_done")
    }
}
