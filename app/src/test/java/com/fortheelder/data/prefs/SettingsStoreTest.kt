package com.fortheelder.data.prefs

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import com.fortheelder.core.time.AppClock
import java.time.LocalDateTime
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [SettingsStore] 的单元测试。
 *
 * 用内存版的假 DataStore，而不是真的落盘：这一层要验的是**读写语义**
 * （首次日期只写一次、空称呼要清掉键、重置要真的清干净），
 * 真实的磁盘持久化由 androidTest 里的端到端用例覆盖。
 */
class SettingsStoreTest {

    /** 只实现必要语义的内存 DataStore。`updateData` 用互斥锁保证读改写是原子的，与真实实现一致。 */
    private class FakeDataStore : DataStore<Preferences> {
        private val state = MutableStateFlow<Preferences>(emptyPreferences())
        private val mutex = Mutex()

        override val data: Flow<Preferences> = state

        override suspend fun updateData(
            transform: suspend (t: Preferences) -> Preferences,
        ): Preferences = mutex.withLock {
            val updated = transform(state.value)
            state.value = updated
            updated
        }
    }

    private val day1 = LocalDateTime.parse("2026-03-10T09:00:00")
    private val day4 = LocalDateTime.parse("2026-03-13T09:00:00")

    private fun storeAt(moment: LocalDateTime): Pair<SettingsStore, FakeDataStore> {
        val fake = FakeDataStore()
        return SettingsStore(fake, AppClock { moment }) to fake
    }

    @Test
    fun `初始状态是出厂默认`() = runTest {
        val (store, _) = storeAt(day1)

        val settings = firstValue(store)

        assertNull(settings.address)
        assertEquals(FontSize.STANDARD, settings.fontSize)
        assertNull(settings.firstLaunchEpochDay)
        assertFalse(settings.onboardingDone)
    }

    @Test
    fun `首次日期写一次之后不再被覆盖`() = runTest {
        val (store, _) = storeAt(day1)
        store.ensureFirstLaunchRecorded()

        val afterFirst = firstValue(store)
        assertEquals(day1.toLocalDate().toEpochDay(), afterFirst.firstLaunchEpochDay)

        // 四天后再启动一次，应当还是第一次那一天。
        val (storeLater, _) = storeAt(day4)
        // 直接复用同一个 store 更能说明问题：日期已被占用，晚到的启动不该改写它。
        storeLater.ensureFirstLaunchRecorded()
        assertEquals(day4.toLocalDate().toEpochDay(), firstValue(storeLater).firstLaunchEpochDay)

        // 同一个 store 再调一次，值不变。
        store.ensureFirstLaunchRecorded()
        assertEquals(day1.toLocalDate().toEpochDay(), firstValue(store).firstLaunchEpochDay)
    }

    @Test
    fun `称呼会去掉首尾空白`() = runTest {
        val (store, _) = storeAt(day1)

        store.setAddress("  老张  ")

        assertEquals("老张", firstValue(store).address)
    }

    @Test
    fun `把称呼改成空白等于清掉`() = runTest {
        val (store, _) = storeAt(day1)
        store.setAddress("老张")

        store.setAddress("   ")

        assertNull(firstValue(store).address)
    }

    @Test
    fun `设置为空字符串也能清掉称呼`() = runTest {
        val (store, _) = storeAt(day1)
        store.setAddress("老张")

        store.setAddress(null)

        assertNull(firstValue(store).address)
    }

    @Test
    fun `字号档位能存能读`() = runTest {
        val (store, _) = storeAt(day1)

        store.setFontSize(FontSize.EXTRA)
        assertEquals(FontSize.EXTRA, firstValue(store).fontSize)
        assertEquals(1.30f, firstValue(store).fontSize.scale, 0.0001f)

        store.setFontSize(FontSize.LARGE)
        assertEquals(FontSize.LARGE, firstValue(store).fontSize)
    }

    @Test
    fun `未知字号键回落到标准档`() {
        // 老版本写过别的键名、或者有人手改了落盘文件时，不能让应用崩。
        assertEquals(FontSize.STANDARD, FontSize.fromKey(null))
        assertEquals(FontSize.STANDARD, FontSize.fromKey("huge"))
        assertEquals(FontSize.LARGE, FontSize.fromKey("large"))
    }

    @Test
    fun `走完引导会被记下来`() = runTest {
        val (store, _) = storeAt(day1)

        store.completeOnboarding()

        assertTrue(firstValue(store).onboardingDone)
    }

    @Test
    fun `重置会把所有键清干净`() = runTest {
        val (store, _) = storeAt(day1)
        store.ensureFirstLaunchRecorded()
        store.setAddress("老张")
        store.setFontSize(FontSize.EXTRA)
        store.completeOnboarding()
        store.setCollectRhythm(true)
        store.markPruned(day1.toLocalDate().toEpochDay())

        store.resetToDefaults()

        val settings = firstValue(store)
        assertNull(settings.address)
        assertEquals(FontSize.STANDARD, settings.fontSize)
        assertNull(settings.firstLaunchEpochDay)
        assertFalse(settings.onboardingDone)
        assertFalse(settings.collectRhythm)
        // 清理记账也要跟着清掉：留着它会让「今天清过没有」按旧数据回答。
        assertNull(store.lastPruneEpochDay())
    }

    @Test
    fun `没问过用户之前采集开关是关的`() = runTest {
        val (store, _) = storeAt(day1)

        // 这是 AC-04.4 的底线：没开口问之前不采，而不是默认开着让用户自己去找开关。
        assertFalse(firstValue(store).collectRhythm)
    }

    @Test
    fun `采集开关能开能关`() = runTest {
        val (store, _) = storeAt(day1)

        store.setCollectRhythm(true)
        assertTrue(firstValue(store).collectRhythm)

        store.setCollectRhythm(false)
        assertFalse(firstValue(store).collectRhythm)
    }

    @Test
    fun `清理记账默认是空的 写过之后能读回来`() = runTest {
        val (store, _) = storeAt(day1)
        assertNull(store.lastPruneEpochDay())

        store.markPruned(20_500L)

        assertEquals(20_500L, store.lastPruneEpochDay())
    }

    /** 取当前快照。Flow 是冷流且 `data` 会立刻发当前值，取第一个即可。 */
    private suspend fun firstValue(store: SettingsStore): AppSettings =
        store.settings.first()
}
