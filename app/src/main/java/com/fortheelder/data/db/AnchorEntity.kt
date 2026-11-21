package com.fortheelder.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 一件用户自己记下来的日常小事（DR-08）。
 *
 * 这张表上**刻意没有**任何计数列：没有连续天数、没有完成次数、没有「最近一次做到」。
 * AC-09.2 要求用户中断之后不显示「已中断 N 天」、不归零、不催人恢复——把这句话做成
 * 结构性的：库里没有地方能算出 N，界面就不可能显示它。这是本条需求最省事的守法。
 *
 * `slot` 存**枚举名字符串**，跟 `wish.status`、`rhythm_signal.kind` 同一条规矩：
 * 以后加一个时段，老数据不会被错读成别的时段。
 *
 * 唯一索引落在 `optionId` 上：一个起点只记一条日常。用户想改时段，删掉再记一次即可——
 * 比在同一件事上维护两个时段更简单，也不会出现「同一件事上午一条、晚上一条」的重复。
 */
@Entity(
    tableName = "anchor",
    indices = [Index(value = ["optionId"], unique = true)],
)
data class AnchorEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val term: String,
    val optionId: String,
    val action: String,
    val slot: String,
    val createdAtEpochMilli: Long,
)
