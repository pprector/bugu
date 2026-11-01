package com.bugu.ai.app

import com.bugu.ai.domain.CopyContext
import com.bugu.ai.domain.CopyOutcome
import com.bugu.ai.domain.DayPart
import com.bugu.ai.infra.WakeupCopyRepository
import com.bugu.ai.infra.WakeupCopyRow
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import org.springframework.dao.DuplicateKeyException
import java.time.Clock
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

/** WakeupCopyService:文案池为空时落最后兜底,并发同键靠唯一索引兜底重查。 */
class WakeupCopyServiceTest {

    private val repository = mock(WakeupCopyRepository::class.java)
    private val aiCopyService = mock(AiCopyService::class.java)
    private val clock = Clock.fixed(
        LocalDateTime.of(2026, 10, 27, 8, 0).atZone(ZoneId.of("Asia/Shanghai")).toInstant(),
        ZoneId.of("Asia/Shanghai"),
    )
    private val service = WakeupCopyService(repository, aiCopyService, clock)
    private val date = LocalDate.of(2026, 10, 27)
    private val context = CopyContext(
        title = "起床",
        scene = WakeupCopyService.SCENE,
        tone = "GENTLE",
        attempt = 1,
        dayPart = DayPart.MORNING,
    )

    @Test
    fun `无文案时落最后兜底`() {
        `when`(repository.findByReminderIdAndCopyDate(1L, date)).thenReturn(null)
        `when`(aiCopyService.composeOutcome(WakeupCopyService.SCENE, "GENTLE", context, WakeupCopyService.SCENE))
            .thenReturn(null)
        `when`(repository.save(any(WakeupCopyRow::class.java))).thenAnswer {
            it.getArgument<WakeupCopyRow>(0).copy(id = 1)
        }

        val row = service.get(1L, date, context)

        assertEquals(WakeupCopyService.FALLBACK_CONTENT, row.content)
        assertTrue(row.degraded)
        assertEquals(date, row.copyDate)
    }

    @Test
    fun `并发同键_重查已落库`() {
        val existing = WakeupCopyRow(
            id = 7,
            reminderId = 1L,
            copyDate = date,
            content = "已缓存文案",
            degraded = false,
        )
        `when`(repository.findByReminderIdAndCopyDate(1L, date)).thenReturn(null, existing)
        `when`(aiCopyService.composeOutcome(WakeupCopyService.SCENE, "GENTLE", context, WakeupCopyService.SCENE))
            .thenReturn(CopyOutcome(content = "新文案", degraded = false))
        `when`(repository.save(any(WakeupCopyRow::class.java))).thenThrow(DuplicateKeyException("dup"))

        val row = service.get(1L, date, context)

        assertSame(existing, row)
    }
}
