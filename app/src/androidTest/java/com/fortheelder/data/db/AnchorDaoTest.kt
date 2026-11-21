package com.fortheelder.data.db

import android.content.Context
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.fortheelder.domain.companion.AnchorSlot
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * `anchor` 表的**真机**验证（DR-08）。
 *
 * 这张表比 `wish` 还简单，但它承担的承诺最重：AC-09.2 要求用户中断之后不显示
 * 「已中断 N 天」、不归零、不催人恢复。做到这件事的办法是**结构性的**——
 * 表上只有六列，其中没有任何一列能数出 N。所以这里除了常规的增删查，
 * 还要把「没有计数列」这件事当断言来跑（见 `AnchorRepositoryTest` 里的列名扫描）。
 *
 * 与 `WishDaoTest` 一样用内存库，理由相同：instrumentation 的测试线程就是主线程，
 * `allowMainThreadQueries()` 是为了让 `runBlocking` 里的 DAO 调用能直接跑。
 */
@RunWith(AndroidJUnit4::class)
class AnchorDaoTest {

    private lateinit var database: ElderDatabase
    private lateinit var anchors: AnchorDao

    @Before
    fun setUp() {
        val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
        database = Room.inMemoryDatabaseBuilder(context, ElderDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        anchors = database.anchorDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun 空的库读出来是空列表() = runBlocking {
        assertEquals(emptyList<AnchorEntity>(), anchors.allFlow().first())
        assertEquals(0, anchors.count())
    }

    @Test
    fun 同一个起点记两次只留一条() = runBlocking {
        val row = anchor()

        val first = anchors.insert(row)
        assertTrue("第一次应当真的写进去了", first > 0L)
        // 第二次同一个 optionId 被唯一索引挡掉。上层的去重判断也不能只靠这个返回值，
        // 但「返回 -1」是界面知道「没记上第二条」的唯一依据。
        assertEquals(-1L, anchors.insert(row))
        assertEquals(1, anchors.count())
    }

    @Test
    fun 不同的起点各占一条() = runBlocking {
        anchors.insert(anchor(optionId = "calligraphy-paper"))
        anchors.insert(anchor(optionId = "calligraphy-pen"))

        assertEquals(2, anchors.count())
    }

    @Test
    fun allFlow按记下的先后升序() = runBlocking {
        anchors.insert(anchor(optionId = "c", action = "第三件"))
        anchors.insert(anchor(optionId = "a", action = "第一件"))
        anchors.insert(anchor(optionId = "b", action = "第二件"))

        // 按 id 排，不按 createdAtEpochMilli 排：界面上不显示时间，但顺序必须是稳定的。
        assertEquals(
            listOf("第三件", "第一件", "第二件"),
            anchors.allFlow().first().map { it.action },
        )
    }

    @Test
    fun 删一条删到删不动() = runBlocking {
        anchors.insert(anchor(optionId = "calligraphy-paper"))
        anchors.insert(anchor(optionId = "calligraphy-pen"))

        assertEquals(1, anchors.deleteByOption("calligraphy-paper"))
        assertEquals(0, anchors.deleteByOption("calligraphy-paper"))
        assertEquals(listOf("calligraphy-pen"), anchors.allFlow().first().map { it.optionId })
    }

    @Test
    fun 删掉之后再记一次是允许的() = runBlocking {
        anchors.insert(anchor(optionId = "calligraphy-paper"))
        anchors.deleteByOption("calligraphy-paper")

        // 用户改主意了：删掉的那条不该留下任何痕迹挡住他再记一遍。
        assertTrue(anchors.insert(anchor(optionId = "calligraphy-paper")) > 0L)
        assertEquals(1, anchors.count())
    }

    @Test
    fun 时段列存的是枚举名而不是序号() = runBlocking {
        // 直接写字符串进去再读出来：往枚举里插一个新时段，老数据不会被错读成别的时段。
        anchors.insert(anchor(slot = AnchorSlot.ANYTIME.name))

        assertEquals("ANYTIME", anchors.allFlow().first().single().slot)
    }

    private fun anchor(
        optionId: String = "calligraphy-paper",
        action: String = "找一张白纸，用自己顺手的笔，从上往下写满它，写什么字都行。",
        slot: String = AnchorSlot.MORNING.name,
    ) = AnchorEntity(
        term = "书法",
        optionId = optionId,
        action = action,
        slot = slot,
        createdAtEpochMilli = 1_000L,
    )
}
