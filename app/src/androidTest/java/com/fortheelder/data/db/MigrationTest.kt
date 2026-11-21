package com.fortheelder.data.db

import android.content.Context
import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
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
 * 这里的两条用例各守一半：
 * 1. 升级**不会丢**老数据：拿第 1 版的 schema 建一个装了作息记录的旧库，跑迁移，
 *    再逐条查出来。顺带确认新表建出来了。
 * 2. 升级**之后新功能真的能用**：老库走应用真正的那条路（Room 打开时自动跑迁移），
 *    然后由 `WishDao` 往新表里写、读回来。只查 `sqlite_master` 确认表存在是不够的——
 *    表建错了列，DAO 的写入会当场失败，而这条用例就是让那个失败发生在这里而不是
 *    在用户手机上。
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

        // 与应用真正一致的那条路：Room 打开旧库时自己跑 MIGRATION_1_2。
        val db = Room.databaseBuilder(context, ElderDatabase::class.java, TEST_DB)
            .addMigrations(MIGRATION_1_2)
            .build()
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

    private companion object {
        const val TEST_DB = "migration-test.db"
    }
}
