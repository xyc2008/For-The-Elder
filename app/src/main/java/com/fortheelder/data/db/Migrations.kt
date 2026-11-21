package com.fortheelder.data.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * 数据库迁移。
 *
 * [ElderDatabase] 的文档注释里写死了一条规矩：**版本号变更必须配一个 `Migration`**，
 * 不允许 `fallbackToDestructiveMigration()`。理由是这个 App 手里的数据是用户攒出来的
 * （作息记录、被记住的意愿），重建库会把它们无声地抹掉，而界面上只会显示
 * 「还没有记录」——错得像是用户的手机自己出问题。
 *
 * 迁移语句与 `app/schemas/.../2.json` 里 Room 生成的建表语句必须**逐字一致**，
 * 否则 `MigrationTestHelper` 的校验会失败。所以这里的两条 SQL 是照 schema 抄的，
 * 不是凭记忆写的。这一点由 `MigrationTest` 守着。
 */

/**
 * 1 → 2：新增 `wish` 表与它的唯一索引（DR-07）。
 *
 * 只建新表，**不动**已有的两张作息表：这一版没有任何列被改名或改类型，
 * 因此升级时用户的作息数据一条都不会丢。
 */
val MIGRATION_1_2: Migration = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `wish` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`kind` TEXT NOT NULL, " +
                "`term` TEXT NOT NULL, " +
                "`evidence` TEXT NOT NULL, " +
                "`sentence` TEXT NOT NULL, " +
                "`status` TEXT NOT NULL, " +
                "`heardAtEpochMilli` INTEGER NOT NULL, " +
                "`decidedAtEpochMilli` INTEGER)"
        )
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_wish_kind_term` ON `wish` (`kind`, `term`)")
    }
}
