package com.fortheelder.data.rhythm

import com.fortheelder.data.db.DailyRhythmEntity
import com.fortheelder.data.db.RhythmSignalEntity
import com.fortheelder.data.db.SpansCodec
import com.fortheelder.domain.engine.rhythm.DailyRhythm
import com.fortheelder.domain.engine.rhythm.RhythmKind
import com.fortheelder.domain.engine.rhythm.RhythmSignal
import com.fortheelder.domain.engine.rhythm.rhythmOffsetToLocalTime
import com.fortheelder.domain.engine.rhythm.toRhythmOffset
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset

/**
 * 领域对象与数据库行之间的换算（ADR-008）。
 *
 * 这里是**唯一**知道「时区」这件事的地方。往上是纯本地时间的领域层，
 * 往下是存绝对时刻的表。两边都不知道对方怎么表示时间——
 * 把换算挤在一个文件里，改起来只要看这一处。
 *
 * 所有「读」方向的换算都可能失败（kind 不认识、spans 坏了），一律返回 null 而不是抛异常：
 * 一条读不出来的老数据，应该表现为「这条信号不存在」，而不是让设置页弹一个崩溃。
 */
internal object RhythmMapping {

    /** 落库：本地时刻 + 采集时刻的时区偏移 → 绝对时刻。 */
    fun toEntity(signal: CapturedSignal, zone: ZoneId): RhythmSignalEntity {
        val offset = zone.rules.getOffset(signal.at)
        return RhythmSignalEntity(
            kind = signal.kind.name,
            atEpochMilli = signal.at.toInstant(offset).toEpochMilli(),
            offsetSeconds = offset.totalSeconds,
        )
    }

    /**
     * 读出：用当时存的偏移把绝对时刻还原成本地时刻。
     *
     * 故意不用「当前时区」还原。用户出差换了时区之后，历史作息仍应显示成
     * 当时看到的那个钟点，否则整幅画像会平移，看起来像作息突然变了。
     *
     * `kind` 认不出来时返回 null：以后往枚举里加种类不会让这里崩，老版本读到新数据
     * 也只是安静地少一条信号。
     */
    fun toDomain(entity: RhythmSignalEntity): RhythmSignal? {
        val kind = RhythmKind.entries.firstOrNull { it.name == entity.kind } ?: return null
        val at = LocalDateTime.ofInstant(
            Instant.ofEpochMilli(entity.atEpochMilli),
            ZoneOffset.ofTotalSeconds(entity.offsetSeconds),
        )
        return RhythmSignal(at = at, kind = kind)
    }

    /** 落库：画像里的本地时刻全部换成「相对作息日起点的分钟数」。 */
    fun toEntity(
        dayEpochDay: Long,
        rhythm: DailyRhythm,
        computedAtEpochMilli: Long,
        cutoverHour: Int,
    ): DailyRhythmEntity = DailyRhythmEntity(
        dayEpochDay = dayEpochDay,
        wakeOffsetMinutes = rhythm.wakeAt.toRhythmOffset(cutoverHour),
        sleepOffsetMinutes = rhythm.sleepAt.toRhythmOffset(cutoverHour),
        activeSpans = SpansCodec.encode(rhythm.activeSpans),
        idleSpans = SpansCodec.encode(rhythm.idleSpans),
        nightChargingOffsetMinutes = rhythm.nightChargingAt?.toRhythmOffset(cutoverHour),
        sampleDays = rhythm.sampleDays,
        confidence = rhythm.confidence,
        computedAtEpochMilli = computedAtEpochMilli,
    )

    /** 读出：偏移量换回本地时刻。spans 解不开就整行作废。 */
    fun toDomain(entity: DailyRhythmEntity, cutoverHour: Int): DailyRhythm? {
        val active = SpansCodec.decode(entity.activeSpans) ?: return null
        val idle = SpansCodec.decode(entity.idleSpans) ?: return null
        return DailyRhythm(
            wakeAt = rhythmOffsetToLocalTime(entity.wakeOffsetMinutes, cutoverHour),
            sleepAt = rhythmOffsetToLocalTime(entity.sleepOffsetMinutes, cutoverHour),
            activeSpans = active,
            idleSpans = idle,
            nightChargingAt = entity.nightChargingOffsetMinutes
                ?.let { rhythmOffsetToLocalTime(it, cutoverHour) },
            sampleDays = entity.sampleDays,
            confidence = entity.confidence,
        )
    }
}
