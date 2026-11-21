package com.fortheelder.domain.companion

import com.fortheelder.domain.wish.WishStatus
import java.time.LocalTime

/** 用户把一步小事记在日常里的哪个时段。 */
enum class AnchorSlot(val label: String) {
    MORNING("上午"),
    AFTERNOON("下午"),
    EVENING("晚上"),
    ANYTIME("想起来的时候"),
    ;

    fun covers(time: LocalTime): Boolean = when (this) {
        MORNING -> !time.isBefore(MORNING_START) && time.isBefore(AFTERNOON_START)
        AFTERNOON -> !time.isBefore(AFTERNOON_START) && time.isBefore(EVENING_START)
        EVENING -> !time.isBefore(EVENING_START) || time.isBefore(MORNING_START)
        ANYTIME -> true
    }

    companion object {
        val MORNING_START: LocalTime = LocalTime.of(5, 0)
        val AFTERNOON_START: LocalTime = LocalTime.of(12, 0)
        val EVENING_START: LocalTime = LocalTime.of(18, 0)

        /** 存储解不出来时的兜底：宁可时段不准，也不能把用户记下的事静默丢掉。 */
        val FALLBACK: AnchorSlot = ANYTIME

        fun byLabel(label: String): AnchorSlot? = entries.firstOrNull { it.label == label }

        fun parse(name: String): AnchorSlot = entries.firstOrNull { it.name == name } ?: FALLBACK
    }
}

/**
 * 一件用户自己记下来的日常小事。
 *
 * 这里**故意没有任何计数列**：没有连续天数、没有完成次数、没有「最近一次」。
 * 用户中断之后，结构上就不可能出现「已中断 N 天」这句话（AC-09.2）。
 */
data class Anchor(
    val id: Long = 0L,
    val term: String,
    val optionId: String,
    val action: String,
    val slot: AnchorSlot,
    val createdAtEpochMilli: Long,
)

/** 还没落库的一条待记事项。 */
data class AnchorDraft(
    val term: String,
    val optionId: String,
    val action: String,
    val slot: AnchorSlot,
)

object AnchorRules {

    /**
     * 只有目录里真的有的起点才能变成锚点。
     * 于是 AC-08.1/08.2/08.3 对锚点自动成立——锚点的动作与耗时就是从起点继承来的。
     */
    fun draftOf(optionId: String, slot: AnchorSlot): AnchorDraft? {
        val option = StarterCatalog.byId(optionId) ?: return null
        return AnchorDraft(term = option.term, optionId = option.id, action = option.firstStep, slot = slot)
    }

    /**
     * 建立锚点必须由用户确认过的意愿推过来：无论自动识别多像，都不许自己建（AC-09.1）。
     */
    fun mayPropose(status: WishStatus, optionId: String): Boolean =
        status == WishStatus.CONFIRMED && StarterCatalog.byId(optionId) != null

    /** 此刻该摆出来的锚点，只挑时段命中的，保持用户建立的先后顺序。 */
    fun dueAt(anchors: List<Anchor>, now: LocalTime): List<Anchor> =
        anchors.filter { it.slot.covers(now) }

    /** AC-09.3：删掉就是删掉，不追问，也不显示「要不要再记回来」。 */
    const val deletionAsksAgain: Boolean = false
}
