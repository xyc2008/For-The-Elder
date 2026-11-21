package com.fortheelder.data.db

import android.content.Context
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 升级路径的**真机**验证（DR-07）。
 *
 * [ElderDatabase] 的注释里立了一条规矩：版本号变更必须配 `Migration`，不许
 * `fallbackToDestructiveMigration()`。理由是升级时重建库会无声地抹掉用户攒下来的
 * 作息记录和意愿，而界面只会显示「还没有记录」——错得像是用户的手机自己出问题。
 * 这条规矩如果没有测试守着，它只是一句注释。
 *
 * 这里的用例分三段：
 * 1. 升级**不会丢**老数据：拿第 1 版的 schema 建一个装了作息记录的旧库，跑迁移，
 *    再逐条查出来。顺带确认新表建出来了。
 * 2. 升级**之后新功能真的能用**：老库走应用真正的那条路（Room 打开时自动跑迁移），
 *    然后由 `WishDao` / `AnchorDao` 往新表里写、读回来。只查 `sqlite_master` 确认表
 *    存在是不够的——表建错了列，DAO 的写入会当场失败，而这条用例就是让那个失败发生
 *    在这里而不是在用户手机上。
 * 3. 每一跳都不许漏：1→2、2→3、1→3 各有一条，因为用户手上可能是任意一个老版本。
 *
 * 第 2 段第一版是用**手拼的** `Room.databaseBuilder(...).addMigrations(MIGRATION_1_2)`
 * 打开的，第 10 轮把库版本升到 3 之后它当场红了（`A migration from 1 to 3 was required
 * but not found`）。那次变红暴露的是用例本身的缺陷：手拼的 builder 自带一份迁移清单，
 * 生产代码漏接 `MIGRATION_2_3` 它照样绿。现在统一走 [ElderDatabase.open]，升级路径只有
 * 一个出口，用例守的就是生产代码真正会跑的那条路。
 *
 * 用独立的库名 [TEST_DB]，不碰应用真正的 `elder.db`，免得把手工验证的状态清掉。
 */
@RunWith(AndroidJUnit4::class)
class MigrationTest {

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        ElderDatabase::class.java,
    )

    private val context: Context
        get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Before
    fun cleanDatabase() {
        context.deleteDatabase(TEST_DB)
    }

    @Test
    fun 从第一版升到第二版原来记的作息一条都不少() {
        // 第 1 版的库里先塞进两条作息信号，模拟一个用了几天、已经有数据的用户。
        helper.createDatabase(TEST_DB, 1).use { old ->
            old.execSQL(
                "INSERT INTO rhythm_signal (kind, atEpochMilli, offsetSeconds) " +
                    "VALUES ('SCREEN_ON', 1000, 28800)"
            )
            old.execSQL(
                "INSERT INTO rhythm_signal (kind, atEpochMilli, offsetSeconds) " +
                    "VALUES ('SCREEN_OFF', 2000, 28800)"
            )
        }

        // 跑迁移，并让 Room 拿第 2 版的 schema 校验升级后的表结构。
        // 校验不过会在这里直接抛，不会等到用例的断言。
        val migrated = helper.runMigrationsAndValidate(TEST_DB, 2, true, MIGRATION_1_2)
        try {
            migrated.query("SELECT COUNT(*) FROM rhythm_signal").use { cursor ->
                cursor.moveToFirst()
                assertEquals("升级不该丢掉任何一条作息记录", 2, cursor.getInt(0))
            }
            migrated.query("SELECT kind FROM rhythm_signal ORDER BY atEpochMilli ASC").use { cursor ->
                cursor.moveToFirst()
                assertEquals("SCREEN_ON", cursor.getString(0))
                cursor.moveToNext()
                assertEquals("SCREEN_OFF", cursor.getString(0))
            }
            migrated.query("SELECT COUNT(*) FROM wish").use { cursor ->
                cursor.moveToFirst()
                assertEquals("新表应当是空的，不是带着别人的数据一起冒出来", 0, cursor.getInt(0))
            }
        } finally {
            migrated.close()
        }
    }

    @Test
    fun 升级之后新表能真的写进去() = runBlocking(Dispatchers.IO) {
        helper.createDatabase(TEST_DB, 1).use { old ->
            old.execSQL(
                "INSERT INTO rhythm_signal (kind, atEpochMilli, offsetSeconds) " +
                    "VALUES ('SCREEN_ON', 1000, 28800)"
            )
        }

        // 与应用真正一致的那条路：Room 打开旧库时自己跑迁移。
        // 这里刻意走 [ElderDatabase.open]，而不是自己拼一个 `databaseBuilder`——
        // 自己拼的话，生产代码里漏接一条 Migration 这些用例也发现不了。
        val db = ElderDatabase.open(context, TEST_DB)
        try {
            assertEquals("升级时不该动到作息记录", 1, db.rhythmSignalDao().count())

            val written = db.wishDao().insertAll(
                listOf(
                    WishEntity(
                        kind = "INTEREST",
                        term = "书法",
                        evidence = "一直想学书法",
                        sentence = "我一直想学书法",
                        status = "PENDING",
                        heardAtEpochMilli = 1_000L,
                    )
                )
            )
            assertTrue("第一次写应当真的落进去", written.single() > 0L)

            val stored = db.wishDao().find("INTEREST", "书法")
            assertNotNull(stored)
            assertEquals("PENDING", stored?.status)
            assertEquals(1_000L, stored?.heardAtEpochMilli)
            assertEquals(1, db.wishDao().count())
        } finally {
            db.close()
        }
    }

    @Test
    fun 从第一版一路升到第三版原来的作息一条都不少() {
        // 第 1 版的库里先塞进两条作息信号，模拟一个用了几天、已经有数据的用户。
        helper.createDatabase(TEST_DB, 1).use { old ->
            old.execSQL(
                "INSERT INTO rhythm_signal (kind, atEpochMilli, offsetSeconds) " +
                    "VALUES ('SCREEN_ON', 1000, 28800)"
            )
            old.execSQL(
                "INSERT INTO rhythm_signal (kind, atEpochMilli, offsetSeconds) " +
                    "VALUES ('SCREEN_OFF', 2000, 28800)"
            )
        }

        // 一次跨两级：用户可能从第 1 版的应用直接装到第 3 版，
        // 那一次打开就要把两张新表都建出来，不能只跑最后一步。
        val migrated = helper.runMigrationsAndValidate(TEST_DB, 3, true, MIGRATION_1_2, MIGRATION_2_3)
        try {
            assertEquals("升级不该丢掉任何一条作息记录", 2, countOf(migrated, "rhythm_signal"))
            assertEquals("新表应当是空的，不是带着别人的数据一起冒出来", 0, countOf(migrated, "wish"))
            assertEquals("新表应当是空的，不是带着别人的数据一起冒出来", 0, countOf(migrated, "anchor"))
        } finally {
            migrated.close()
        }
    }

    @Test
    fun 从第二版升到第三版被记住的意愿还在() {
        helper.createDatabase(TEST_DB, 2).use { old ->
            old.execSQL(
                "INSERT INTO wish (kind, term, evidence, sentence, status, heardAtEpochMilli, decidedAtEpochMilli) " +
                    "VALUES ('INTEREST', '书法', '一直想学书法', '我一直想学书法', 'CONFIRMED', 1000, 2000)"
            )
        }

        val migrated = helper.runMigrationsAndValidate(TEST_DB, 3, true, MIGRATION_2_3)
        try {
            assertEquals(1, countOf(migrated, "wish"))
            migrated.query("SELECT status, decidedAtEpochMilli FROM wish").use { cursor ->
                cursor.moveToFirst()
                assertEquals("用户表过态的结果在升级后变了", "CONFIRMED", cursor.getString(0))
                assertEquals("表过态的时刻在升级后变了", 2_000L, cursor.getLong(1))
            }
            assertEquals(0, countOf(migrated, "anchor"))
        } finally {
            migrated.close()
        }
    }

    @Test
    fun 升到第三版之后锚点能真的写进去() = runBlocking(Dispatchers.IO) {
        helper.createDatabase(TEST_DB, 2).use { old ->
            old.execSQL(
                "INSERT INTO wish (kind, term, evidence, sentence, status, heardAtEpochMilli) " +
                    "VALUES ('INTEREST', '书法', '一直想学书法', '我一直想学书法', 'CONFIRMED', 1000)"
            )
        }

        // 与应用真正一致的那条路：Room 打开第 2 版的库时自己跑 MIGRATION_2_3。
        // 只查 sqlite_master 确认表存在是不够的——表建错了列，DAO 的写入会当场失败，
        // 这条用例就是让那个失败发生在这里，而不是在用户手机上。
        val db = ElderDatabase.open(context, TEST_DB)
        try {
            assertEquals("升级时不该动到被记住的意愿", 1, db.wishDao().count())

            val row = AnchorEntity(
                term = "书法",
                optionId = "calligraphy-paper",
                action = "找一张白纸，用自己顺手的笔，从上往下写满它，写什么字都行。",
                slot = "MORNING",
                createdAtEpochMilli = 1_000L,
            )
            assertTrue("第一次写应当真的落进去", db.anchorDao().insert(row) > 0L)
            assertEquals("同一个起点不该记成两条", -1L, db.anchorDao().insert(row))

            val stored = db.anchorDao().all().single()
            assertEquals("MORNING", stored.slot)
            assertEquals(1_000L, stored.createdAtEpochMilli)
            assertEquals(1, db.anchorDao().count())
        } finally {
            db.close()
        }
    }

    private fun countOf(db: SupportSQLiteDatabase, table: String): Int =
        db.query("SELECT COUNT(*) FROM $table").use { cursor ->
            cursor.moveToFirst()
            cursor.getInt(0)
        }

    private companion object {
        const val TEST_DB = "migration-test.db"
    }
}
