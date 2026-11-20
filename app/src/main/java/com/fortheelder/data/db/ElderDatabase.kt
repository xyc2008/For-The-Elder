package com.fortheelder.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

/**
 * 常伴的本地数据库（DR-01~DR-06）。
 *
 * 只有两张表，都只和作息有关。**没有用户表、没有账号表、没有内容表**——
 * 这个 App 不上传任何东西，也就没有需要和服务端对齐的结构。
 *
 * `exportSchema = true`：schema 的 JSON 会落到 `app/schemas/`，跟着代码一起提交。
 * 这是为了在写第 2 版 schema 时能对照第 1 版，而不是靠回忆去写迁移语句。
 * 目录由 `app/build.gradle.kts` 的 `ksp { arg("room.schemaLocation", ...) }` 指定。
 *
 * 版本 1 是首个版本，没有迁移。往后的版本号变更必须配上 `Migration`，不允许
 * `fallbackToDestructiveMigration()`——那会在升级时无声地删掉用户的作息数据，
 * 而界面上还会显示「已记录 N 条」变成 0，看起来像谁的错都不是。
 */
@Database(
    entities = [RhythmSignalEntity::class, DailyRhythmEntity::class],
    version = 1,
    exportSchema = true,
)
abstract class ElderDatabase : RoomDatabase() {

    abstract fun rhythmSignalDao(): RhythmSignalDao

    abstract fun dailyRhythmDao(): DailyRhythmDao

    companion object {

        const val FILE_NAME = "elder.db"

        fun open(context: Context): ElderDatabase =
            Room.databaseBuilder(context.applicationContext, ElderDatabase::class.java, FILE_NAME)
                .build()
    }
}
