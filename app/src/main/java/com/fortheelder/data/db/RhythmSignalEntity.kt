package com.fortheelder.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 一条作息事件（DR-05）。
 *
 * 存的是**绝对时刻 + 当时的时区偏移**，而不是「本地时间字符串」：
 * 前者的排序、去重、30 天清理都不依赖时区，后者在用户改时区后会指向另一个瞬间。
 * 偏移单独存一列，是让本地时刻能被原样还原——推算出的作息画像必须和用户看到的
 * 手机时间一致，不能因为后来换了个时区就把历史作息平移掉。
 *
 * `kind` 存字符串而不是枚举序号：以后往 [com.fortheelder.domain.engine.rhythm.RhythmKind]
 * 里插一种新信号时，老数据不会被错读成别的种类。
 *
 * 唯一索引 `(kind, atEpochMilli)` 是给同一个广播重复投递准备的：系统在某些情况下会把
 * 一次亮屏报两遍，重复的事件会让「这天信号数」虚高，直接影响有效天的判定。
 * 采集器不需要自己去重，交给数据库。
 */
@Entity(
    tableName = "rhythm_signal",
    indices = [
        Index(value = ["kind", "atEpochMilli"], unique = true),
        Index(value = ["atEpochMilli"]),
    ],
)
data class RhythmSignalEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val kind: String,
    val atEpochMilli: Long,
    val offsetSeconds: Int,
)
