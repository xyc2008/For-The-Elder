package com.fortheelder.data.db

import com.fortheelder.domain.engine.rhythm.TimeSpan
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

/**
 * 时段的文本编解码（DR-06）。
 *
 * 存成 `HH:mm-HH:mm,HH:mm-HH:mm` 这种一眼能看懂的形式，代价是解析可能失败。
 * [decode] 用「返回 null」而不是抛异常表达失败：数据库里一行坏数据不该让设置页崩掉，
 * 上层看到 null 就当这天的画像不存在，等下一次推断重算即可（画像是派生态，丢了不心疼）。
 */
internal object SpansCodec {

    private val HHMM: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

    /** 空列表编成空串——空串与「字段是空的」在数据库里本就该是同一种东西。 */
    fun encode(spans: List<TimeSpan>): String = spans.joinToString(separator = ",") { it.toString() }

    /**
     * @return 解析出的时段；**行损坏时返回 null**。
     *
     * 判为损坏的情况：段里没有 `-`、时刻不是两位数的 `HH:mm`、跨天段为空段
     * （`07:00-07:00`）。最后一条和 [TimeSpan] 的说明一致——正常推断不会产出空段，
     * 出现了就说明这行被人手改过或者写坏了。
     */
    fun decode(raw: String): List<TimeSpan>? {
        val text = raw.trim()
        if (text.isEmpty()) return emptyList()
        val spans = ArrayList<TimeSpan>()
        for (chunk in text.split(',')) {
            val parts = chunk.trim().split('-')
            if (parts.size != 2) return null
            val start = parseTime(parts[0]) ?: return null
            val end = parseTime(parts[1]) ?: return null
            if (start == end) return null
            spans.add(TimeSpan(start, end))
        }
        return spans
    }

    private fun parseTime(text: String): LocalTime? =
        try {
            LocalTime.parse(text.trim(), HHMM)
        } catch (_: DateTimeParseException) {
            null
        }
}
