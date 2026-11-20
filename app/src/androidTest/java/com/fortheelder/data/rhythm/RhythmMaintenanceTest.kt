package com.fortheelder.data.rhythm

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.fortheelder.core.time.AppClock
import com.fortheelder.data.db.ElderDatabase
import com.fortheelder.data.prefs.SettingsStore
import com.fortheelder.domain.engine.rhythm.RhythmKind
import java.io.File
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 「今天清过没有」这件事的设备侧验证。
 *
 * 用**真的 DataStore 文件**而不是内存假件：这里要验的正是「今天已经清过」这件事被记住了，
 * 而不是每次调用都扫一遍全表。文件名每次唯一（[System.nanoTime]），
 * 因为同一个文件上不能同时有两个 DataStore 活着，同一个测试类里建两次会直接抛异常。
 *
 * [now] 是本类的成员变量，用例中途能把它拨到第二天，从而验出「跨天会重新清一次」；
 * 仓库、设置、清理器读的都是这一个来源。
 */
@RunWith(AndroidJUnit4::class)
class RhythmMaintenanceTest {

    private lateinit var context: Context
    private lateinit var database: ElderDatabase
    private lateinit var repository: RhythmRepository
    private lateinit var dataStore: DataStore<Preferences>
    private lateinit var dataStoreFile: File
    private lateinit var storeScope: CoroutineScope
    private lateinit var settings: SettingsStore

    private val zone: ZoneId = ZoneId.of("Asia/Shanghai")
    private var now: LocalDateTime = LocalDateTime.of(2026, 3, 10, 9, 0)

    /** 过期数据：2026-01-01，离「现在」已经远超 30 天的保留期。 */
    private val stale: LocalDateTime = LocalDateTime.of(2026, 1, 1, 7, 0)

    @Before
    fun setUp() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        context.deleteDatabase(ElderDatabase.FILE_NAME)
        database = ElderDatabase.open(context)
        repository = RhythmRepository(database, AppClock { now }, zone)
        dataStoreFile = File(context.cacheDir, "maintenance-${System.nanoTime()}.preferences_pb")
        storeScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        dataStore = PreferenceDataStoreFactory.create(scope = storeScope) { dataStoreFile }
        settings = SettingsStore(dataStore, AppClock { now })
    }

    @After
    fun tearDown() {
        storeScope.cancel()
        database.close()
        context.deleteDatabase(ElderDatabase.FILE_NAME)
        dataStoreFile.delete()
    }

    @Test
    fun `第一次检查会真删过期数据并记下今天`() = runBlocking(Dispatchers.IO) {
        repository.record(listOf(CapturedSignal(stale, RhythmKind.SCREEN_ON)))
        repository.record(
            listOf(CapturedSignal(LocalDateTime.of(2026, 3, 9, 7, 0), RhythmKind.SCREEN_ON))
        )
        assertNull(settings.lastPruneEpochDay())

        // 分界点是 04:00，所以 3 月 10 日 09:00 属于作息日 3 月 10 日。
        assertEquals(1, maintenance().pruneIfDue())

        assertEquals(1, repository.signalCountOnce())
        assertEquals(LocalDate.of(2026, 3, 10).toEpochDay(), settings.lastPruneEpochDay())
    }

    @Test
    fun `同一天里再检查一次不清理也不重复记账`() = runBlocking(Dispatchers.IO) {
        assertEquals(0, maintenance().pruneIfDue())

        // 塞一条过期数据，再检查一次：今天已经查过了，这条不该被动。
        repository.record(listOf(CapturedSignal(stale, RhythmKind.SCREEN_ON)))

        assertEquals(0, maintenance().pruneIfDue())
        assertEquals(1, repository.signalCountOnce())
    }

    @Test
    fun `跨到下一个作息日会重新检查`() = runBlocking(Dispatchers.IO) {
        repository.record(listOf(CapturedSignal(stale, RhythmKind.SCREEN_ON)))
        assertEquals(1, maintenance().pruneIfDue())

        // 把时钟拨到第二天：分界点 04:00 意味着 3 月 11 日 09:00 已经是新的作息日。
        now = LocalDateTime.of(2026, 3, 11, 9, 0)
        repository.record(listOf(CapturedSignal(stale.plusDays(1), RhythmKind.SCREEN_ON)))

        assertEquals(1, maintenance().pruneIfDue())
        assertEquals(0, repository.signalCountOnce())
        assertEquals(LocalDate.of(2026, 3, 11).toEpochDay(), settings.lastPruneEpochDay())
    }

    private fun maintenance() = RhythmMaintenance(repository, settings, AppClock { now })
}
