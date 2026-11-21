package com.fortheelder.data.db

import android.content.Context
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.fortheelder.domain.wish.WishStatus
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * `wish` 表的**真机**验证。
 *
 * 这张表上真正要守住的不是某一条 SQL，而是三条结构性保证：
 * 1. 同一件事只存一条（靠 `(kind, term)` 的唯一索引，不靠上层先查后插）；
 * 2. 已定的状态不会被后来的重复提到冲掉（靠 `INSERT OR IGNORE`）；
 * 3. 表态只在「当前状态正好等于调用方以为的那个」时才生效（靠带 `AND status = :from` 的乐观 UPDATE）。
 *
 * 这三条都只在真的建了库之后才作数，所以和 `RhythmDaoTest` 一样用内存库在设备上跑。
 * `allowMainThreadQueries()` 的理由也一样：instrumentation 的测试线程就是主线程。
 */
@RunWith(AndroidJUnit4::class)
class WishDaoTest {

    private lateinit var database: ElderDatabase
    private lateinit var wishes: WishDao

    @Before
    fun setUp() {
        val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
        database = Room.inMemoryDatabaseBuilder(context, ElderDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        wishes = database.wishDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun 空的库读出来是空列表() = runBlocking {
        assertEquals(emptyList<WishEntity>(), wishes.allFlow().first())
        assertEquals(0, wishes.count())
        assertNull(wishes.find("INTEREST", "书法"))
    }

    @Test
    fun 同一件事记两次只留一条() = runBlocking {
        val row = wish()

        // 第一次拿到的 rowId 必须是真的（> 0），第二次同一个 (kind, term) 必须被忽略（-1）。
        // 两次都写同一个 `wish()`：不能把断言建立在「又一次插入」上，那一次拿到的本来就该是 -1。
        val first = wishes.insertAll(listOf(row))
        assertEquals(1, first.size)
        assertTrue("第一次应当真的写进去了", first.single() > 0L)
        assertEquals(listOf(-1L), wishes.insertAll(listOf(row)))
        assertEquals(1, wishes.count())
    }

    @Test
    fun 同一个词条在不同类别下算两件事() = runBlocking {
        wishes.insertAll(
            listOf(
                wish(kind = "INTEREST", term = "睡不着"),
                wish(kind = "STATE", term = "睡不着"),
            )
        )
        assertEquals(2, wishes.count())
    }

    @Test
    fun 已定的状态不会被后来的重复提到冲掉() = runBlocking {
        wishes.insertAll(listOf(wish()))
        assertEquals(1, wishes.decide("INTEREST", "书法", "PENDING", "CONFIRMED", 2_000L))

        // 三个月后又提了一遍「想学书法」：插入被唯一索引挡掉，状态也没被改回 PENDING。
        assertEquals(listOf(-1L), wishes.insertAll(listOf(wish(status = WishStatus.PENDING.name))))

        assertEquals(WishStatus.CONFIRMED.name, wishes.find("INTEREST", "书法")?.status)
        assertEquals(2_000L, wishes.find("INTEREST", "书法")?.decidedAtEpochMilli)
    }

    @Test
    fun 表态只在当前状态对得上时才生效() = runBlocking {
        wishes.insertAll(listOf(wish()))

        assertEquals(1, wishes.decide("INTEREST", "书法", "PENDING", "DECLINED", 2_000L))
        // 同一个按钮又点了一次：`from` 还是 PENDING，但库里已经是 DECLINED，改不动。
        assertEquals(0, wishes.decide("INTEREST", "书法", "PENDING", "DECLINED", 3_000L))

        assertEquals(WishStatus.DECLINED.name, wishes.find("INTEREST", "书法")?.status)
        assertEquals("第二次没有改到数据，就不该刷新表态时刻", 2_000L, wishes.find("INTEREST", "书法")?.decidedAtEpochMilli)
    }

    @Test
    fun 表态一条不存在的意愿改动零行() = runBlocking {
        assertEquals(0, wishes.decide("INTEREST", "书法", "PENDING", "CONFIRMED", 2_000L))
        assertEquals(0, wishes.count())
    }

    @Test
    fun 表态会写下那一刻的绝对毫秒() = runBlocking {
        wishes.insertAll(listOf(wish()))
        wishes.decide("INTEREST", "书法", "PENDING", "CONFIRMED", 1_772_000_000_000L)

        val stored = wishes.find("INTEREST", "书法")
        assertNotNull(stored)
        assertEquals(1_772_000_000_000L, stored?.decidedAtEpochMilli)
    }

    @Test
    fun allFlow按首次听到的先后升序() = runBlocking {
        wishes.insertAll(
            listOf(
                wish(term = "书法", heardAtEpochMilli = 3_000L),
                wish(term = "摄影", heardAtEpochMilli = 1_000L),
                wish(term = "下棋", heardAtEpochMilli = 2_000L),
            )
        )

        // 按 id 排，不按 heardAtEpochMilli 排：写入的时刻是可以被伪造/补录的，
        // 而「先听到哪一件」在用户心里就是那个顺序，界面要不显示时间也得有稳定次序。
        assertEquals(listOf("书法", "摄影", "下棋"), wishes.allFlow().first().map { it.term })
    }

    @Test
    fun delete只删指定的一条() = runBlocking {
        wishes.insertAll(listOf(wish(term = "书法"), wish(term = "摄影")))

        assertEquals(1, wishes.delete("INTEREST", "书法"))
        assertEquals(0, wishes.delete("INTEREST", "书法"))
        assertEquals(listOf("摄影"), wishes.allFlow().first().map { it.term })
    }

    @Test
    fun deleteAll一件都不留() = runBlocking {
        wishes.insertAll(listOf(wish(term = "书法"), wish(term = "摄影")))

        wishes.deleteAll()

        assertEquals(0, wishes.count())
        assertEquals(emptyList<WishEntity>(), wishes.allFlow().first())
    }

    @Test
    fun 状态列存的是枚举名而不是序号() = runBlocking {
        // 直接写字符串进去再读出来：往枚举里插一个新状态时，老数据不会被错读成别的状态。
        wishes.insertAll(listOf(wish(status = "DECLINED")))

        assertEquals("DECLINED", wishes.find("INTEREST", "书法")?.status)
        assertEquals(1, wishes.count())
    }

    private fun wish(
        kind: String = "INTEREST",
        term: String = "书法",
        status: String = WishStatus.PENDING.name,
        heardAtEpochMilli: Long = 1_000L,
    ) = WishEntity(
        kind = kind,
        term = term,
        evidence = "一直想学书法",
        sentence = "我一直想学书法",
        status = status,
        heardAtEpochMilli = heardAtEpochMilli,
        decidedAtEpochMilli = null,
    )
}
