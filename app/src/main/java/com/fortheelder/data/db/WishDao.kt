package com.fortheelder.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * 意愿的读写（DR-07）。
 *
 * [decide] 的那条 `UPDATE` 是这张表上唯一会改数据的语句，它带 `AND status = :from`：
 * 只有**当前状态正好等于调用方以为的那个**时才生效。这样即使两次表态挤在一起
 * （用户连点两下、或者补录的写入还在路上），第二次也不会把一个已经落定的状态
 * 再盖一遍，而是返回 0 行。改动只有一处、且是乐观并发，就不需要给这张表加锁。
 */
@Dao
interface WishDao {

    /**
     * 记下一批意愿，已经记过的（同一个 `kind` + `term`）跳过。
     *
     * 返回每个位置的 rowId，被跳过的是 -1。**已决定的那些永远不会被后来的重复提到
     * 覆盖**——这不是靠上层判断，是靠 `INSERT OR IGNORE` 与唯一索引从结构上保证的。
     */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(wishes: List<WishEntity>): List<Long>

    @Query("SELECT * FROM wish WHERE kind = :kind AND term = :term LIMIT 1")
    suspend fun find(kind: String, term: String): WishEntity?

    /**
     * 改一条意愿的状态，返回真正改到行数。
     *
     * 返回 0 有两种情况：这条意愿根本不在（用户点得比落库还快），或者它已经不是
     * [from] 了（重复点）。两种都应当被调用方当成「这次点击没有生效」处理，
     * 而不是硬写一遍。
     */
    @Query(
        "UPDATE wish SET status = :to, decidedAtEpochMilli = :at " +
            "WHERE kind = :kind AND term = :term AND status = :from"
    )
    suspend fun decide(kind: String, term: String, from: String, to: String, at: Long): Int

    /** 「常伴记住的事」。按首次听到的先后排，界面不显示时间也能有稳定顺序。 */
    @Query("SELECT * FROM wish ORDER BY id ASC")
    fun allFlow(): Flow<List<WishEntity>>

    @Query("SELECT COUNT(*) FROM wish")
    suspend fun count(): Int

    /** 用户点「不用记了」。删一条。 */
    @Query("DELETE FROM wish WHERE kind = :kind AND term = :term")
    suspend fun delete(kind: String, term: String): Int

    /** 用户点「全部清空」。 */
    @Query("DELETE FROM wish")
    suspend fun deleteAll()
}
