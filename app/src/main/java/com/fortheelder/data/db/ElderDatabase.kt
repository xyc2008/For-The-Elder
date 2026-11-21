package com.fortheelder.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

/**
 * 常伴的本地数据库（DR-05~DR-07）。
 *
 * 四张表：两张记作息，一张记被记住的意愿，一张记用户自己定的日常小事。
 * **没有用户表、没有账号表、没有内容表**——这个 App 不上传任何东西，也就没有需要和
 * 服务端对齐的结构。
 *
 * `exportSchema = true`：schema 的 JSON 会落到 `app/schemas/`，跟着代码一起提交。
 * 这是为了在写下一版 schema 时能对照上一版，而不是靠回忆去写迁移语句。
 * 目录由 `app/build.gradle.kts` 的 `ksp { arg("room.schemaLocation", ...) }` 指定。
 *
 * **版本号变更必须配上 `Migration`**（见 [MIGRATION_1_2]），不允许
 * `fallbackToDestructiveMigration()`——那会在升级时无声地删掉用户攒下来的数据，
 * 而界面上还会显示「还没有记录」，看起来像谁的错都不是。
 * 这条规矩由 `MigrationTest` 守着：它拿上一版的 schema 建旧库、跑迁移、再让 Room
 * 校验表结构，表结构对不上就直接红。
 *
 * [open] 的 `name` 默认就是正式应用那个库文件，**仪器化测试要传自己的名字**。这不是
 * 洁癖：几个 androidTest 会在同一个进程里 `deleteDatabase` 再重开同一个文件，而应用
 * 容器的 Room 实例一旦建好就一直在。两者共用 `elder.db` 时，真机上出现过
 * `no such table: wish` 的崩溃，事后留下的库是个 `user_version=0`、只剩
 * `android_metadata` 的空壳——同一个测试进程里两方争一个文件，谁先删谁后开都会变。
 */
@Database(
    entities = [RhythmSignalEntity::class, DailyRhythmEntity::class, WishEntity::class, AnchorEntity::class],
    version = 3,
    exportSchema = true,
)
abstract class ElderDatabase : RoomDatabase() {

    abstract fun rhythmSignalDao(): RhythmSignalDao

    abstract fun dailyRhythmDao(): DailyRhythmDao

    abstract fun wishDao(): WishDao

    abstract fun anchorDao(): AnchorDao

    companion object {

        const val FILE_NAME = "elder.db"

        fun open(context: Context, name: String = FILE_NAME): ElderDatabase =
            Room.databaseBuilder(context.applicationContext, ElderDatabase::class.java, name)
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                .build()
    }
}
