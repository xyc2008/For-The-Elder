package com.fortheelder.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 一条被记住的意愿（DR-07）。
 *
 * `status` 存**枚举名字符串**而不是序号：往
 * [com.fortheelder.domain.wish.WishStatus] 里插一个新状态时，老数据不会被错读成
 * 别的状态。这跟 `rhythm_signal.kind` 是同一条规矩。
 *
 * 唯一索引 `(kind, term)` 是「同一件事只记一条」的**结构性保证**：用户今天说
 * 「想学书法」，三个月后又说一遍，还是同一条，状态也不会被这次重复提到冲掉。
 * 并发下也不会出现两条——去重交给数据库，不靠上层先查后插。
 *
 * 时间只存绝对毫秒，不存本地时刻，也不带时区偏移：这张表上没有任何一处需要把
 * 时间还原成本地时刻显示，存了就是给以后「显示时间」留后门。
 */
@Entity(
    tableName = "wish",
    indices = [Index(value = ["kind", "term"], unique = true)],
)
data class WishEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val kind: String,
    val term: String,
    val evidence: String,
    val sentence: String,
    val status: String,
    val heardAtEpochMilli: Long,
    val decidedAtEpochMilli: Long? = null,
)
