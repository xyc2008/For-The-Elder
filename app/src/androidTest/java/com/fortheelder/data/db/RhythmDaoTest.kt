package com.fortheelder.data.db

import android.content.Context
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * DAO 的**真机**验证。
 *
 * 纯 JVM 测不了 Room：SQL 是 Room 在编译期生成的，唯一索引、`INSERT OR IGNORE`、
 * `Flow` 的失效通知这些行为只有真的建了库才作数。所以这里用内存库在设备上跑。
 *
 * 用 `allowMainThreadQueries()` 是因为 instrumentation 的测试线程就是主线程，
 * 不放行的话 Room 会直接抛「Cannot access database on the main thread」，
 * 而那和被测逻辑无关。真机应用里没有这行——`RhythmRepositoryTest` 用的就是
 * 正常配置的库，顺带证明不许主线程访问这条约束还在。
 */
@RunWith(AndroidJUnit4::class)
class RhythmDaoTest {

    private lateinit var database: ElderDatabase
    private lateinit var signals: RhythmSignalDao
    private lateinit var rhythms: DailyRhythmDao

    @Before
    fun setUp() {
        val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
        database = Room.inMemoryDatabaseBuilder(context, ElderDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        signals = database.rhythmSignalDao()
        rhythms = database.dailyRhythmDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun 空的库读出来是空列表() = runBlocking {
        assertEquals(emptyList<RhythmSignalEntity>(), signals.since(0L))
        assertEquals(0, signals.count())
        assertNull(rhythms.latest())
    }

    @Test
    fun 同一个种类同一个时刻只落一条() = runBlocking {
        val row = signal(kind = "SCREEN_ON", atEpochMilli = 1_000L)

        assertEquals(listOf(1L), signals.insertAll(listOf(row.copy(id = 0L))))
        assertEquals(listOf(-1L), signals.insertAll(listOf(row.copy(id = 0L))))
        assertEquals(1, signals.count())
    }

    @Test
    fun 不同种类同一时刻各留一条() = runBlocking {
        signals.insertAll(
            listOf(
                signal(kind = "SCREEN_ON", atEpochMilli = 1_000L),
                signal(kind = "USER_PRESENT", atEpochMilli = 1_000L),
            )
        )
        assertEquals(2, signals.count())
    }

    @Test
    fun 批量插入返回真正写进去的条数() = runBlocking {
        signals.insertAll(listOf(signal("SCREEN_ON", 1_000L)))
        val written = signals.insertAll(
            listOf(
                signal("SCREEN_ON", 1_000L),
                signal("SCREEN_OFF", 2_000L),
                signal("CHARGING_START", 3_000L),
            )
        )
        // 冲突那一行返回 -1；真正写进去的两行返回正数行号。
        // 行号是**不连续**的：设备上实测是 [-1, 3, 4]，不是想当然的 [-1, 2, 3]——
        // `id` 是 AUTOINCREMENT，被 IGNORE 掉的那次插入也已经把序号用掉了。
        // 所以这里只断言「哪些位置真的写进去了」，不断言具体行号。
        assertEquals(3, written.size)
        assertEquals(-1L, written[0])
        assertTrue("第二条应当真的写进去了", written[1] > 0L)
        assertTrue("第三条应当真的写进去了", written[2] > 0L)
        assertTrue("后写入的行号应当更大", written[2] > written[1])
        assertEquals(3, signals.count())
    }

    @Test
    fun since只返回窗口之内的并按时刻升序() = runBlocking {
        signals.insertAll(
            listOf(
                signal("SCREEN_ON", 3_000L),
                signal("SCREEN_ON", 1_000L),
                signal("SCREEN_ON", 2_000L),
            )
        )
        val window = signals.since(2_000L)
        assertEquals(listOf(2_000L, 3_000L), window.map { it.atEpochMilli })
    }

    @Test
    fun since的边界是闭区间() = runBlocking {
        signals.insertAll(listOf(signal("SCREEN_ON", 2_000L)))
        assertEquals(1, signals.since(2_000L).size)
        assertEquals(0, signals.since(2_001L).size)
    }

    @Test
    fun deleteBefore只删更早的() = runBlocking {
        signals.insertAll(
            listOf(
                signal("SCREEN_ON", 1_000L),
                signal("SCREEN_ON", 2_000L),
                signal("SCREEN_ON", 3_000L),
            )
        )
        assertEquals(1, signals.deleteBefore(2_000L))
        assertEquals(listOf(2_000L, 3_000L), signals.since(0L).map { it.atEpochMilli })
    }

    @Test
    fun deleteAll清空信号但不动画像() = runBlocking {
        signals.insertAll(listOf(signal("SCREEN_ON", 1_000L)))
        rhythms.upsert(listOf(rhythm(dayEpochDay = 100L)))

        signals.deleteAll()

        assertEquals(0, signals.count())
        assertEquals(1, rhythms.count())
    }

    @Test
    fun 同一天的画像重复写会整行覆盖() = runBlocking {
        rhythms.upsert(listOf(rhythm(dayEpochDay = 100L, wakeOffsetMinutes = 150)))
        rhythms.upsert(listOf(rhythm(dayEpochDay = 100L, wakeOffsetMinutes = 200)))

        assertEquals(1, rhythms.count())
        assertEquals(200, rhythms.byDay(100L)?.wakeOffsetMinutes)
    }

    @Test
    fun latest取最近那一天的画像() = runBlocking {
        rhythms.upsert(
            listOf(
                rhythm(dayEpochDay = 100L, wakeOffsetMinutes = 150),
                rhythm(dayEpochDay = 300L, wakeOffsetMinutes = 170),
                rhythm(dayEpochDay = 200L, wakeOffsetMinutes = 160),
            )
        )

        assertEquals(300L, rhythms.latest()?.dayEpochDay)
        assertEquals(170, rhythms.latest()?.wakeOffsetMinutes)
    }

    @Test
    fun 画像的deleteBefore只删更早的天() = runBlocking {
        rhythms.upsert(
            listOf(
                rhythm(dayEpochDay = 100L),
                rhythm(dayEpochDay = 200L),
                rhythm(dayEpochDay = 300L),
            )
        )
        assertEquals(1, rhythms.deleteBefore(150L))
        assertNull(rhythms.byDay(100L))
        assertNotNull(rhythms.byDay(200L))
    }

    @Test
    fun countFlow每次写入后重新发出条数() = runBlocking {
        val seen = mutableListOf<Int>()
        val collector = launch(Dispatchers.IO) { signals.countFlow().collect { seen += it } }
        try {
            awaitUntil("初次发出") { seen.isNotEmpty() }
            assertEquals(0, seen.last())

            signals.insertAll(listOf(signal("SCREEN_ON", 1_000L)))
            awaitUntil("插入后涨到 1") { seen.lastOrNull() == 1 }

            signals.insertAll(listOf(signal("SCREEN_OFF", 2_000L)))
            awaitUntil("再插入后涨到 2") { seen.lastOrNull() == 2 }

            signals.deleteAll()
            awaitUntil("清空后回到 0") { seen.lastOrNull() == 0 }
        } finally {
            collector.cancel()
        }
    }

    @Test
    fun 直接查一次countFlow也能拿到当前值() = runBlocking {
        signals.insertAll(listOf(signal("SCREEN_ON", 1_000L)))
        assertEquals(1, signals.countFlow().first())
    }

    private suspend fun awaitUntil(what: String, condition: () -> Boolean) {
        withTimeout(AWAIT_TIMEOUT_MILLIS) {
            while (!condition()) delay(POLL_MILLIS)
        }
    }

    private fun signal(kind: String, atEpochMilli: Long) = RhythmSignalEntity(
        kind = kind,
        atEpochMilli = atEpochMilli,
        offsetSeconds = 8 * 3600,
    )

    private fun rhythm(dayEpochDay: Long, wakeOffsetMinutes: Int = 150) = DailyRhythmEntity(
        dayEpochDay = dayEpochDay,
        wakeOffsetMinutes = wakeOffsetMinutes,
        sleepOffsetMinutes = 1110,
        activeSpans = "07:00-11:00",
        idleSpans = "14:00-16:00",
        nightChargingOffsetMinutes = 1140,
        sampleDays = 5,
        confidence = 0.75f,
        computedAtEpochMilli = 1_000L,
    )

    private companion object {
        const val AWAIT_TIMEOUT_MILLIS = 5_000L
        const val POLL_MILLIS = 20L
    }
}
