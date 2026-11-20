package com.fortheelder.core.time

import java.time.LocalDate
import java.time.LocalDateTime

/**
 * 可注入的时钟。
 *
 * 应用里所有「现在几点」「今天是哪天」的判断都必须走这里，不允许直接调
 * [LocalDateTime.now]。原因有两个：
 * 1. 问候语、作息时段、干预窗口的判断全部依赖时间，直接调系统时间就无法写单元测试；
 * 2. 用户在系统设置里改时区/时间后，行为应当一致，集中在一处便于复查。
 */
fun interface AppClock {
    fun now(): LocalDateTime
}

/** 生产环境使用的时钟。 */
object SystemAppClock : AppClock {
    override fun now(): LocalDateTime = LocalDateTime.now()
}

/** 把 [LocalDateTime] 归一成「天」，用于计算天数差。 */
fun LocalDateTime.toEpochDay(): Long = LocalDate.of(year, monthValue, dayOfMonth).toEpochDay()
