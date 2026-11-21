package com.fortheelder.data.anchor

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.fortheelder.core.time.AppClock
import com.fortheelder.data.db.ElderDatabase
import com.fortheelder.domain.companion.AnchorRules
import com.fortheelder.domain.companion.AnchorSlot
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * 生活锚点仓库的**真机**验证，用的是和正式应用同一个 [ElderDatabase.open]。
 *
 * 这里要证明的是仓库替上层扛住的三件事：
 * 1. **只有用户亲手记下的才会在里面**——写入的唯一入口要一份 [AnchorRules.draftOf]
 *    造出来的草稿，而草稿只能从目录里的起点生成（AC-09.1）；
 * 2. **时段是真的在起作用**——[AnchorRepository.dueNow] 只在时段命中时给出来，
 *    用户把一件事记在晚上，早上就不会看见它；
 * 3. **表上算不出「中断了几天」**——列名整体钉死，见 `锚点表里没有任何能数出天数或次数的列`。
 *    这条是 AC-09.2 的守法本身，不是附带检查。
 *
 * 用自己的库文件 `elder-test-anchor-repository.db`，不碰应用那个 `elder.db`。
 */
@RunWith(AndroidJUnit4::class)
class AnchorRepositoryTest {

    private lateinit var context: Context
    private lateinit var database: ElderDatabase
    private lateinit var repository: AnchorRepository

    /** 固定时区与固定的「现在」，理由与 `WishRepositoryTest` 相同。 */
    private val zone: ZoneId = ZoneId.of("Asia/Shanghai")
    private var now: LocalDateTime = LocalDateTime.of(2026, 3, 10, 9, 0)

    private val paper = AnchorRules.draftOf("calligraphy-paper", AnchorSlot.MORNING)!!

    @Before
    fun setUp() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        context.deleteDatabase(testDb)
        database = ElderDatabase.open(context, testDb)
        repository = AnchorRepository(database, AppClock { now }, zone)
    }

    @After
    fun tearDown() {
        database.close()
        context.deleteDatabase(testDb)
    }

    @Test
    fun 记下一条之后流里就有了() = runBlocking {
        assertEquals(emptyList<String>(), repository.anchors.first().map { it.optionId })

        assertTrue(repository.add(paper))

        val stored = repository.anchors.first().single()
        assertEquals("calligraphy-paper", stored.optionId)
        assertEquals("书法", stored.term)
        assertEquals(paper.action, stored.action)
        assertEquals(AnchorSlot.MORNING, stored.slot)
        // 时刻来自注入的时钟与注入的时区：2026-03-10 09:00 +08:00 == 1773104400000。
        // 这个数是拿 python 的 datetime 独立算出来的，不是从应用代码里抄的——
        // 抄一遍就只是证明「它等于它自己」，算不出来时区换算到底对不对。
        assertEquals(1_773_104_400_000L, stored.createdAtEpochMilli)
    }

    @Test
    fun 同一个起点记两次第二次不算数() = runBlocking {
        assertTrue(repository.add(paper))
        // 界面不该假装又记了一条：第二次返回 false，库里还是一条。
        assertFalse(repository.add(paper))
        assertEquals(1, repository.count())
    }

    @Test
    fun 不用记了之后真的没了() = runBlocking {
        repository.add(paper)

        assertTrue(repository.remove("calligraphy-paper"))
        // 再点一次「不用记了」：没有可删的了，返回 false，不报错也不追问（AC-09.3）。
        assertFalse(repository.remove("calligraphy-paper"))
        assertEquals(emptyList<String>(), repository.anchors.first().map { it.optionId })
    }

    @Test
    fun 目录里没有的起点造不出草稿() {
        // 这是「锚点只能由用户从目录里点出来」的第一道门：目录里没有，连草稿都没有。
        assertEquals(null, AnchorRules.draftOf("not-in-catalog", AnchorSlot.MORNING))
    }

    @Test
    fun dueNow只给出时段命中的那些() = runBlocking {
        repository.add(AnchorRules.draftOf("calligraphy-paper", AnchorSlot.MORNING)!!)
        repository.add(AnchorRules.draftOf("calligraphy-pen", AnchorSlot.EVENING)!!)
        repository.add(AnchorRules.draftOf("chess-solo", AnchorSlot.ANYTIME)!!)

        // 上午九点：记在上午的、和「想起来就做」的该出现；记在晚上的不该出现。
        assertEquals(
            listOf("calligraphy-paper", "chess-solo"),
            repository.dueNow(now).map { it.optionId },
        )

        // 把「现在」拨到晚上八点：换成晚上那条命中，上午那条安静退场。
        now = LocalDateTime.of(2026, 3, 10, 20, 0)
        assertEquals(
            listOf("calligraphy-pen", "chess-solo"),
            repository.dueNow(now).map { it.optionId },
        )
    }

    @Test
    fun 锚点表里没有任何能数出天数或次数的列() = runBlocking {
        // AC-09.2 的守法：不是「界面上不显示已中断 N 天」，而是库里根本没有地方能算出 N。
        // 所以这里把列名整体钉死——以后谁加一列 `doneCount` 或 `lastDoneAtEpochMilli`，
        // 这条用例当场红，而不是等用户看见一句「您好几天没做了」。
        val columns = columnNames()

        assertEquals(
            listOf("id", "term", "optionId", "action", "slot", "createdAtEpochMilli"),
            columns,
        )
        val countingWords = listOf("count", "streak", "done", "complete", "last", "recent", "total", "times")
        for (column in columns) {
            for (word in countingWords) {
                assertFalse(
                    "锚点表里出现了带计数语义的列 `$column`（命中 `$word`）",
                    column.lowercase().contains(word),
                )
            }
        }
    }

    private fun columnNames(): List<String> = buildList {
        database.openHelper.readableDatabase.query("PRAGMA table_info(anchor)").use { cursor ->
            val nameIndex = cursor.getColumnIndexOrThrow("name")
            while (cursor.moveToNext()) add(cursor.getString(nameIndex))
        }
    }

    private companion object {
        /** 自己的库文件；原因见 `ElderDatabase.open` 的注释。 */
        const val testDb = "elder-test-anchor-repository.db"
    }
}
