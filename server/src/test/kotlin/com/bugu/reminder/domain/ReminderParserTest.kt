package com.bugu.reminder.domain

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.LocalDateTime
import java.time.ZoneId

/** ReminderParser 桩:固定时钟 2026-10-26 15:00,验证日期词/时段/钟点/类型/事项提取。 */
class ReminderParserTest {

    private val clock = Clock.fixed(
        LocalDateTime.of(2026, 10, 26, 15, 0).atZone(ZoneId.of("Asia/Shanghai")).toInstant(),
        ZoneId.of("Asia/Shanghai"),
    )
    private val parser = ReminderParser(clock)

    @Test
    fun `明早九点提醒我妈吃降压药`() {
        val draft = parser.parse("明早九点提醒我妈吃降压药")
        assertFalse(draft.needsManual)
        assertEquals(LocalDateTime.of(2026, 10, 27, 9, 0), draft.fireAt)
        assertEquals("我妈吃降压药", draft.title)
        assertEquals(ReminderKind.ONCE, draft.kind)
        assertNull(draft.repeatRule)
    }

    @Test
    fun `每天晚上8点半吃药识别为REPEAT`() {
        val draft = parser.parse("每天晚上8点半吃药")
        assertFalse(draft.needsManual)
        assertEquals(ReminderKind.REPEAT, draft.kind)
        assertEquals("DAILY", draft.repeatRule)
        assertEquals(LocalDateTime.of(2026, 10, 27, 20, 30), draft.fireAt)
        assertEquals("吃药", draft.title)
    }

    @Test
    fun `后天下午三点开会`() {
        val draft = parser.parse("后天下午三点开会")
        assertEquals(LocalDateTime.of(2026, 10, 28, 15, 0), draft.fireAt)
        assertEquals("开会", draft.title)
    }

    @Test
    fun `HH_mm格式与今晚日期词`() {
        val draft = parser.parse("今晚21:30 给手机充电")
        assertEquals(LocalDateTime.of(2026, 10, 26, 21, 30), draft.fireAt)
    }

    @Test
    fun `无日期词默认明天避免过去时刻`() {
        val draft = parser.parse("明早提醒我吃药")
        assertFalse(draft.needsManual)
        assertEquals(LocalDateTime.of(2026, 10, 27, 8, 0), draft.fireAt)
    }

    @Test
    fun `无时间词低置信回手动`() {
        val draft = parser.parse("提醒他妈吃药")
        assertTrue(draft.needsManual)
        assertNull(draft.fireAt)
    }

    @Test
    fun `无意义输入低置信`() {
        val draft = parser.parse("asdfg")
        assertTrue(draft.needsManual)
        assertNull(draft.fireAt)
    }

    @Test
    fun `尾句记得提取为便签且标题剥离`() {
        val draft = parser.parse("明早八点提醒妈妈吃降压药,记得多喝点水")
        assertFalse(draft.needsManual)
        assertEquals(LocalDateTime.of(2026, 10, 27, 8, 0), draft.fireAt)
        assertEquals("妈妈吃降压药", draft.title)
        assertEquals("记得多喝点水", draft.note)
    }

    @Test
    fun `时间词剥离后残留空白收敛为单空格`() {
        val draft = parser.parse("明天 下午三点 开会")
        assertFalse(draft.needsManual)
        assertEquals(LocalDateTime.of(2026, 10, 27, 15, 0), draft.fireAt)
        assertEquals("开会", draft.title)
    }
}
