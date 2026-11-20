package com.fortheelder.data.rhythm

import com.fortheelder.domain.engine.rhythm.RhythmKind
import java.time.LocalDateTime

/**
 * 采集器交出来的一条信号。
 *
 * 只有「本地时刻 + 种类」这两项，和领域层的
 * [com.fortheelder.domain.engine.rhythm.RhythmSignal] 看着一样，但这是有意的：
 * 采集侧还会带上「这次采集发生时手机的时区」，而这个换算属于数据层
 * （见 ADR-008）——采集器不该知道时区怎么存，领域层不该知道时区存在。
 *
 * [at] 是**设备本地时间**（`LocalDateTime` 不带时区）。落库时由
 * [RhythmMapping.toEntity] 用采集时刻的时区偏移把它变成绝对时刻。
 */
data class CapturedSignal(
    val at: LocalDateTime,
    val kind: RhythmKind,
)
