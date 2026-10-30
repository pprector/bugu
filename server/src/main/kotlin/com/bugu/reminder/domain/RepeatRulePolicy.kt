package com.bugu.reminder.domain

import java.time.DayOfWeek
import java.time.LocalDateTime

/**
 * 重复规则最小表达(epic Decision,2026-10-26):`DAILY`,或 `WEEKLY:` 逗号分隔星期缩写
 * (MON..SUN,≥1 天、不重复)。更复杂规则 P1 再扩。
 */
object RepeatRulePolicy {

    private val WEEKDAYS = setOf("MON", "TUE", "WED", "THU", "FRI", "SAT", "SUN")

    private val DAY_OF_WEEK = mapOf(
        "MON" to DayOfWeek.MONDAY,
        "TUE" to DayOfWeek.TUESDAY,
        "WED" to DayOfWeek.WEDNESDAY,
        "THU" to DayOfWeek.THURSDAY,
        "FRI" to DayOfWeek.FRIDAY,
        "SAT" to DayOfWeek.SATURDAY,
        "SUN" to DayOfWeek.SUNDAY,
    )

    fun isValid(rule: String): Boolean {
        if (rule == "DAILY") return true
        if (!rule.startsWith("WEEKLY:")) return false
        val days = rule.removePrefix("WEEKLY:").split(",")
        return days.all { it in WEEKDAYS } && days.size == days.toSet().size
    }

    /**
     * 下一次触发时刻(E10 决策:确认后与错过收口后都要续期)。
     * `from` 为本次触发时刻,`after` 为当前时刻——若后续轮次已落在过去(服务停机/连续错过),
     * 直接跳到 `after` 之后的第一个候选,避免生成「一落库就已逾期」的 fire。
     */
    fun nextOccurrence(rule: String, from: LocalDateTime, after: LocalDateTime): LocalDateTime {
        require(isValid(rule)) { "重复规则非法: $rule" }
        var next = from.plusDays(1)
        if (rule == "DAILY") {
            while (!next.isAfter(after)) next = next.plusDays(1)
            return next
        }
        val days = rule.removePrefix("WEEKLY:").split(",").map { DAY_OF_WEEK.getValue(it) }.toSet()
        // 允许日一周内必出现,14 天窗口足以覆盖「连续错过多个周期」
        repeat(14) {
            if (next.isAfter(after) && next.dayOfWeek in days) return next
            next = next.plusDays(1)
        }
        return next
    }
}
