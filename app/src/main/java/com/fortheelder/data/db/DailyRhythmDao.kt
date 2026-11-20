package com.fortheelder.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

/**
 * 作息画像的读写（DR-06）。
 *
 * 画像一天一行，重复推断同一天直接整行替换（[OnConflictStrategy.REPLACE]）：
 * 重算是正常的（信号补齐了就该重算），不需要保留历史版本——
 * 留着「上个月的作息」既没用，又会诱使后面的功能去比较「最近作息变了没有」，
 * 那种比较一旦做出来，就会变成对用户的评价。
 */
@Dao
interface DailyRhythmDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(rows: List<DailyRhythmEntity>)

    @Query("SELECT * FROM daily_rhythm WHERE dayEpochDay = :dayEpochDay LIMIT 1")
    suspend fun byDay(dayEpochDay: Long): DailyRhythmEntity?

    @Query("SELECT * FROM daily_rhythm ORDER BY dayEpochDay DESC LIMIT 1")
    suspend fun latest(): DailyRhythmEntity?

    @Query("SELECT COUNT(*) FROM daily_rhythm")
    suspend fun count(): Int

    @Query("DELETE FROM daily_rhythm WHERE dayEpochDay < :dayEpochDay")
    suspend fun deleteBefore(dayEpochDay: Long): Int

    @Query("DELETE FROM daily_rhythm")
    suspend fun deleteAll()
}
