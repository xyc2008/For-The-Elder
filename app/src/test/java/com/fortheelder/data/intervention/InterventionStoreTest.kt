package com.fortheelder.data.intervention

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import com.fortheelder.core.time.AppClock
import java.io.FileNotFoundException
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * [InterventionStore] 的单元测试。
 *
 * 这一层要验的是**读写语义**，不是判断规则——判断在
 * [com.fortheelder.domain.engine.intervention.InterventionWindow] 那边，已经被穷举守着。
 * 所以这里只问四件事：
 *
 * 1. 空文件读出来是「什么都没发生过」，而不是某个默认时刻；
 * 2. 记下的时刻**原样**回得来（存的是 epoch 毫秒，跨时区还原要不差分毫）；
 * 3. 忽略计数的加减确实经过 [`afterSpoken`] / [`afterAnswered`]，
 *    并且这一层真的会把它写下去，不是只在内存里改；
 * 4. 文件里是坏数据（负数）时读回来要收敛，免得判定层拿到一个负的间隔。
 *
 * 用内存版的假 DataStore 而不是真的落盘：真落盘由 androidTest 的端到端用例覆盖，
 * 这里只想验语义。假实现用互斥锁保证 `updateData` 的读改写真原子，与真实实现一致。
 */
class InterventionStoreTest {

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

    /**
     * 前 [failures] 次收集 `data` 时抛 [FileNotFoundException]、之后正常的假 DataStore。
     *
     * 抛的是**真的** `java.io.FileNotFoundException`，不是自造的异常类型：要验的正是
     * DataStore 那个特定分支抛出来的那个特定异常能被接住，换成别的类型等于没测。
     *
     * [failures] 把两种情形分开：`1` 是「文件刚出现」，够重试一次就好；
     * 一个大数是「一直读不出来」，用来验重试是有上限的。
     */
    private class FlakyReadDataStore(private val failures: Int) : DataStore<Preferences> {
        private val state = MutableStateFlow<Preferences>(emptyPreferences())
        private val mutex = Mutex()
        private val collections = AtomicInteger(0)

        override val data: Flow<Preferences> = flow {
            if (collections.incrementAndGet() <= failures) {
                throw FileNotFoundException(
                    "cache/intervention.preferences_pb: open failed: ENOENT (No such file or directory)",
                )
            }
            emitAll(state)
        }

        /** 上游被收集了几次。用来证明重试**真的发生了**，而不是碰巧没抛。 */
        val collectionCount: Int get() = collections.get()

        override suspend fun updateData(
            transform: suspend (t: Preferences) -> Preferences,
        ): Preferences = mutex.withLock {
            val updated = transform(state.value)
            state.value = updated
            updated
        }
    }

    /** 可以往前拨的手表。真实时钟不归测试管，但「隔了多久」是这一层的核心输入。 */
    private class FakeClock(private var moment: LocalDateTime) : AppClock {
        override fun now(): LocalDateTime = moment

        fun moveTo(next: LocalDateTime) {
            moment = next
        }
    }

    /** 固定时区：跟系统时区无关，测试才不是「在我机器上过」。 */
    private val zone: ZoneId = ZoneId.of("Asia/Shanghai")

    private val morning = LocalDateTime.parse("2026-03-10T09:00:00")
    private val noon = LocalDateTime.parse("2026-03-10T12:00:00")
    private val evening = LocalDateTime.parse("2026-03-10T20:30:00")

    private fun storeWith(clock: FakeClock, dataStore: FakeDataStore = FakeDataStore()) =
        InterventionStore(dataStore, clock, zone) to dataStore

    @Test
    fun `空文件读出来是什么都没发生过`() = runTest {
        val (store, _) = storeWith(FakeClock(morning))

        val state = store.stateOnce()

        assertNull(state.lastSpokenAt)
        assertNull(state.lastAnsweredAt)
        assertEquals(0, state.ignoredStreak)
    }

    @Test
    fun `记一次开口 留下的是当下的时刻`() = runTest {
        val (store, _) = storeWith(FakeClock(morning))

        val state = store.recordSpoken()

        assertEquals(morning, state.lastSpokenAt)
        // 第一次开口没有任何「被忽略」的证据。
        assertEquals(0, state.ignoredStreak)
        assertNull(state.lastAnsweredAt)
    }

    @Test
    fun `时刻原样回得来 不差秒也不差时区`() = runTest {
        val (store, _) = storeWith(FakeClock(morning))
        store.recordSpoken()

        assertEquals(morning, store.stateOnce().lastSpokenAt)
    }

    @Test
    fun `连续两次没人接话 忽略计数加一并落盘`() = runTest {
        val clock = FakeClock(morning)
        val (store, _) = storeWith(clock)

        store.recordSpoken()
        clock.moveTo(noon)
        val second = store.recordSpoken()

        assertEquals(1, second.ignoredStreak)
        assertEquals(noon, second.lastSpokenAt)
        // 再读一次文件：加一这件事必须真的写下去了。
        assertEquals(1, store.stateOnce().ignoredStreak)
    }

    @Test
    fun `接话会把忽略计数清零并记下时刻`() = runTest {
        val clock = FakeClock(morning)
        val (store, _) = storeWith(clock)

        store.recordSpoken()
        clock.moveTo(noon)
        store.recordSpoken()
        clock.moveTo(evening)
        val answered = store.recordAnswered()

        assertEquals(0, answered.ignoredStreak)
        assertEquals(evening, answered.lastAnsweredAt)
        // 接话不改「上次开口」，那是另一件事实。
        assertEquals(noon, answered.lastSpokenAt)
    }

    @Test
    fun `接话之后再开口 不再算被忽略`() = runTest {
        val clock = FakeClock(morning)
        val (store, _) = storeWith(clock)

        store.recordSpoken()
        clock.moveTo(noon)
        store.recordAnswered()
        clock.moveTo(evening)
        val next = store.recordSpoken()

        assertEquals(0, next.ignoredStreak)
        assertEquals(evening, next.lastSpokenAt)
    }

    @Test
    fun `状态能从一个新的读取者那里读回来`() = runTest {
        // 用户在首页被招呼过、又去「说两句」里回了一句，回到首页时是**另一个**
        // 读取者（新的 stateOnce 调用）。文件里是什么，它就必须看到什么。
        val clock = FakeClock(morning)
        val (first, dataStore) = storeWith(clock)
        first.recordSpoken()
        clock.moveTo(noon)
        first.recordAnswered()

        val (second, _) = storeWith(clock, dataStore)
        val state = second.stateOnce()

        assertEquals(morning, state.lastSpokenAt)
        assertEquals(noon, state.lastAnsweredAt)
        assertEquals(0, state.ignoredStreak)
    }

    @Test
    fun `state 这条流会跟着写入更新`() = runTest {
        val (store, _) = storeWith(FakeClock(morning))

        store.recordSpoken()

        assertEquals(morning, store.state.first().lastSpokenAt)
    }

    @Test
    fun `文件里是负数时 读回来收敛到零`() = runTest {
        // 手改过的文件、或者上一版写坏的数据，都不该让间隔被算得更短。
        // 这里直接往假 DataStore 里塞一个负数，绕开本类的写入路径。
        val (store, dataStore) = storeWith(FakeClock(morning))
        val streakKey = intPreferencesKey("ignored_streak")
        dataStore.updateData { it.toMutablePreferences().apply { set(streakKey, -5) } }

        assertEquals(0, store.stateOnce().ignoredStreak)
    }

    @Test
    fun `时钟往前拨之后接话再开口 计数仍是零`() = runTest {
        // 用户把手机时间往后调了一天（比如换时区）——不该因此多记一次「被忽略」。
        val clock = FakeClock(morning)
        val (store, _) = storeWith(clock)

        store.recordSpoken()
        clock.moveTo(morning.plusDays(1))
        store.recordAnswered()
        clock.moveTo(morning.plusDays(1).plusMinutes(1))
        val next = store.recordSpoken()

        assertEquals(0, next.ignoredStreak)
        assertEquals(morning.plusDays(1).plusMinutes(1), next.lastSpokenAt)
    }

    @Test
    fun `首次读撞上首次写的结果 重试一次就能读到`() = runTest {
        // 真机上复现过的竞态（见 records/03 ADR-033）：读的 `open` 打不开还没生成的
        // 文件，而就在这中间，写入的那次 rename 落地了，于是 OkioReadScope.readData()
        // 把 FileNotFoundException 抛了出来。数据没坏，文件其实已经在磁盘上了。
        val dataStore = FlakyReadDataStore(failures = 1)
        val store = InterventionStore(dataStore, FakeClock(morning), zone)

        val state = store.stateOnce()

        assertNull(state.lastSpokenAt)
        assertNull(state.lastAnsweredAt)
        assertEquals(0, state.ignoredStreak)
        // 两次：失败的那次 + 重试成功的那次。
        assertEquals(2, dataStore.collectionCount)
    }

    @Test
    fun `state 这条流也扛得住同一次竞态`() = runTest {
        // 界面走的是 `state` 这条流，不是 stateOnce。两条入口都得接上同一个护栏，
        // 少接一条就会有「首页不崩、说两句崩」这种半截现象。
        val dataStore = FlakyReadDataStore(failures = 1)
        val store = InterventionStore(dataStore, FakeClock(morning), zone)

        assertNull(store.state.first().lastSpokenAt)
        assertEquals(2, dataStore.collectionCount)
    }

    @Test
    fun `一直读不出来时 只重试一次就把异常抛出去`() = runTest {
        // 重试是给「文件刚出现」这一步擦屁股的，不是给真故障兜底的。
        // 要是无休止地重试，磁盘满、权限错这类问题会被这层藏起来，永远看不见。
        val dataStore = FlakyReadDataStore(failures = Int.MAX_VALUE)
        val store = InterventionStore(dataStore, FakeClock(morning), zone)

        var thrown: Throwable? = null
        try {
            store.stateOnce()
        } catch (ex: Throwable) {
            thrown = ex
        }

        assertEquals(FileNotFoundException::class.java, thrown?.javaClass)
        // 一次失败 + 一次重试，之后就放弃，不再往下试第三次。
        assertEquals(2, dataStore.collectionCount)
    }
}
