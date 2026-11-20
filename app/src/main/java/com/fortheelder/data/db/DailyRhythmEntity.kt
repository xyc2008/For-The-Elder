package com.fortheelder.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 一天的作息画像（DR-06）。
 *
 * 这是**派生态**：随时可以拿 [RhythmSignalEntity] 重算出来，所以它坏掉了不算数据丢失，
 * 清掉重算即可。存它只是为了避免每次开界面都跑一遍推断。
 *
 * 时刻一律存「相对作息日起点的分钟数」（0..1439），不存 `LocalTime` 也不存时间戳——
 * 作息日以凌晨 [com.fortheelder.domain.engine.rhythm.RhythmConfig.DEFAULT_CUTOVER_HOUR]
 * 点为界，偏移量与分界点的关系必须写死在同一套换算里（见 `toRhythmOffset`）。
 *
 * `activeSpans` / `idleSpans` 存成 `HH:mm-HH:mm,HH:mm-HH:mm` 这样的文本，而不是二进制或
 * 序列化对象：出事的时候能用 `sqlite3` 直接看出来里面是什么，这一点比省几十个字节重要。
 *
 * @param dayEpochDay 作息日的日期（epoch day）。同一天重复推断会整行覆盖。
 */
@Entity(tableName = "daily_rhythm")
data class DailyRhythmEntity(
    @PrimaryKey val dayEpochDay: Long,
    val wakeOffsetMinutes: Int,
    val sleepOffsetMinutes: Int,
    val activeSpans: String,
    val idleSpans: String,
    val nightChargingOffsetMinutes: Int?,
    val sampleDays: Int,
    val confidence: Float,
    val computedAtEpochMilli: Long,
)
