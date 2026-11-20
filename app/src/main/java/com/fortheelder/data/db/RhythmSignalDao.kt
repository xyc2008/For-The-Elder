package com.fortheelder.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * 作息事件的读写（DR-05）。
 *
 * 这里刻意不做「按天查询」：调用方要的是「最近 N 天的信号」，而作息日的边界
 * 不是数据库该知道的事。查询按绝对时刻切，切在哪由 [com.fortheelder.data.rhythm.RhythmRepository]
 * 按作息日算好再传进来。
 */
@Dao
interface RhythmSignalDao {

    /**
     * 批量落库，遇到完全相同的事件就跳过。
     *
     * 返回每个位置的 rowId，被跳过的是 -1。调用方据此知道**真正新增了几条**——
     * 这个数字要显示给用户（设置页的「已记录 N 条」），不能算上重复的。
     *
     * 行号**不保证连续**：`id` 是 AUTOINCREMENT，被 IGNORE 掉的那次插入也把序号用掉了。
     * 设备上实测插「[重复, 新, 新]」返回 `[-1, 3, 4]`，而不是想当然的 `[-1, 2, 3]`。
     * 所以「写进去几条」只能数正数，不能拿行号当计数器。
     */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(signals: List<RhythmSignalEntity>): List<Long>

    @Query("SELECT * FROM rhythm_signal WHERE atEpochMilli >= :sinceEpochMilli ORDER BY atEpochMilli ASC")
    suspend fun since(sinceEpochMilli: Long): List<RhythmSignalEntity>

    @Query("SELECT COUNT(*) FROM rhythm_signal")
    suspend fun count(): Int

    /** 观测用：设置页显示「已记录 N 条」，改一下立刻能看到。 */
    @Query("SELECT COUNT(*) FROM rhythm_signal")
    fun countFlow(): Flow<Int>

    @Query("DELETE FROM rhythm_signal WHERE atEpochMilli < :cutoffEpochMilli")
    suspend fun deleteBefore(cutoffEpochMilli: Long): Int

    @Query("DELETE FROM rhythm_signal")
    suspend fun deleteAll()
}
