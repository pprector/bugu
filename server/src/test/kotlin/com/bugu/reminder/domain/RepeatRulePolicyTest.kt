package com.bugu.reminder.domain

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.DayOfWeek
import java.time.LocalDateTime

/** RepeatRulePolicy 纯函数:DAILY / WEEKLY:星期缩写 合法性与边界。 */
class RepeatRulePolicyTest {

    @Test
    fun `DAILY 合法`() {
        assertTrue(RepeatRulePolicy.isValid("DAILY"))
    }

    @Test
    fun `WEEKLY 单天与多天合法`() {
        assertTrue(RepeatRulePolicy.isValid("WEEKLY:MON"))
        assertTrue(RepeatRulePolicy.isValid("WEEKLY:MON,WED,FRI"))
    }

    @Test
    fun `非法规则拒绝`() {
        assertFalse(RepeatRulePolicy.isValid(""))
        assertFalse(RepeatRulePolicy.isValid("DAILY:EXTRA"))
        assertFalse(RepeatRulePolicy.isValid("WEEKLY:"))
        assertFalse(RepeatRulePolicy.isValid("WEEKLY:MON,"))
        assertFalse(RepeatRulePolicy.isValid("WEEKLY:XXX"))
        assertFalse(RepeatRulePolicy.isValid("WEEKLY:MON,MON"))
        assertFalse(RepeatRulePolicy.isValid("WEEKLY:mon"))
        assertFalse(RepeatRulePolicy.isValid("HOURLY:1"))
    }

    @Test
    fun `DAILY 下一次为次日_连续错过后跳到未来`() {
        val from = LocalDateTime.of(2026, 10, 27, 8, 0)
        // 当天确认:下一次就是次日同一时刻
        assertEquals(
            LocalDateTime.of(2026, 10, 28, 8, 0),
            RepeatRulePolicy.nextOccurrence("DAILY", from, LocalDateTime.of(2026, 10, 27, 8, 5)),
        )
        // 停机 3 天:直接跳到当前时刻之后的第一个候选,不产出已逾期的 fire
        assertEquals(
            LocalDateTime.of(2026, 10, 31, 8, 0),
            RepeatRulePolicy.nextOccurrence("DAILY", from, LocalDateTime.of(2026, 10, 30, 12, 0)),
        )
    }

    @Test
    fun `WEEKLY 下一次落在允许的星期且晚于当前`() {
        // 2026-10-27 是周二;规则 MON,WED → 下一次为周三 10-28
        val from = LocalDateTime.of(2026, 10, 27, 9, 0)
        val next = RepeatRulePolicy.nextOccurrence("WEEKLY:MON,WED", from, LocalDateTime.of(2026, 10, 27, 9, 30))
        assertEquals(LocalDateTime.of(2026, 10, 28, 9, 0), next)
        assertEquals(DayOfWeek.WEDNESDAY, next.dayOfWeek)

        // 从周三确认:下一次为下周一(跳过周四~周日)
        val afterWed = RepeatRulePolicy.nextOccurrence(
            "WEEKLY:MON,WED", LocalDateTime.of(2026, 10, 28, 9, 0), LocalDateTime.of(2026, 10, 28, 9, 5),
        )
        assertEquals(LocalDateTime.of(2026, 11, 2, 9, 0), afterWed)
        assertEquals(DayOfWeek.MONDAY, afterWed.dayOfWeek)
    }
}
