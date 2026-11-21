package com.fortheelder.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * 生活锚点的读写（DR-08）。
 *
 * 这张表上**没有 UPDATE**：锚点只有两种命运——被用户记下来，或者被用户删掉。
 * 没有「完成」也没有「跳过」要记录，所以不需要改任何一行。少了这条语句，
 * 「已中断 N 天」这类显示在数据层就无处生根。
 */
@Dao
interface AnchorDao {

    /** 返回 rowId；同一个 `optionId` 已经记过时返回 -1。 */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(anchor: AnchorEntity): Long

    /** 「常伴记着的日常」。按用户记下的先后排，界面不显示时间也能有稳定顺序。 */
    @Query("SELECT * FROM anchor ORDER BY id ASC")
    fun allFlow(): Flow<List<AnchorEntity>>

    @Query("SELECT * FROM anchor ORDER BY id ASC")
    suspend fun all(): List<AnchorEntity>

    /** 用户点「不用记了」。删一条，返回真正删到的行数。 */
    @Query("DELETE FROM anchor WHERE optionId = :optionId")
    suspend fun deleteByOption(optionId: String): Int

    @Query("SELECT COUNT(*) FROM anchor")
    suspend fun count(): Int
}
