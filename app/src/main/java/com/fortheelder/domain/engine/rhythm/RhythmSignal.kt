package com.fortheelder.domain.engine.rhythm

import java.time.LocalDateTime

/**
 * 作息信号的种类。
 *
 * 只有这五种，理由是它们都能从**运行时注册的系统广播**里拿到，
 * 不需要任何「读取应用使用记录」的权限。少一种信号不会让作息画像失效，
 * 但多要一个权限会让这个 App 变成监视工具——见 docs/01 的 FR-04「明确不做」。
 */
enum class RhythmKind {
    SCREEN_ON,
    SCREEN_OFF,
    USER_PRESENT,
    CHARGING_START,
    CHARGING_END,
    ;

    /**
     * 是否表示「人手在手机上」。
     *
     * [SCREEN_ON] 也算，因为目标用户里相当一部分不设锁屏密码，
     * 那种情况下根本不会收到 [USER_PRESENT]。代价是亮屏统计会把「兜里误触亮屏」
     * 也算进来，靠每天最少信号条数那一道门槛兜住。
     */
    val isPresence: Boolean get() = this == SCREEN_ON || this == USER_PRESENT
}

/**
 * 一条作息信号。
 *
 * [at] 是**设备本地时间**。换算时区不是引擎的事：采集器在落库前就换算好，
 * 引擎只做纯粹的本地时间算术。这样引擎不依赖任何 Android API，
 * 单测里用合成数据就能跑（docs/01 的 AC-04.1）。
 */
data class RhythmSignal(val at: LocalDateTime, val kind: RhythmKind)
