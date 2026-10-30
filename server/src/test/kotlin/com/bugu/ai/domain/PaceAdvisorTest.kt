package com.bugu.ai.domain

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.Duration

/** PaceAdvisor 纯函数:空记录零偏移、秒回提前、慢响应放宽、中间档不动、±5min 封顶。 */
class PaceAdvisorTest {

    @Test
    fun `无记录零偏移`() {
        assertEquals(Duration.ZERO, PaceAdvisor.shift(emptyList()))
    }

    @Test
    fun `秒回者提前5分钟`() {
        val delays = listOf(Duration.ofSeconds(30), Duration.ofSeconds(60), Duration.ofSeconds(119))
        assertEquals(Duration.ofMinutes(-5), PaceAdvisor.shift(delays))
    }

    @Test
    fun `慢响应者放宽5分钟`() {
        val delays = listOf(Duration.ofSeconds(900), Duration.ofSeconds(601))
        assertEquals(Duration.ofMinutes(5), PaceAdvisor.shift(delays))
    }

    @Test
    fun `中间档不动`() {
        val delays = listOf(Duration.ofSeconds(300), Duration.ofSeconds(400))
        assertEquals(Duration.ZERO, PaceAdvisor.shift(delays))
    }

    @Test
    fun `偏移不超过正负5分钟`() {
        assertEquals(Duration.ofMinutes(5), PaceAdvisor.shift(listOf(Duration.ofHours(2))))
        assertEquals(Duration.ofMinutes(-5), PaceAdvisor.shift(listOf(Duration.ofSeconds(1))))
    }

    @Test
    fun `混合记录按均值分档`() {
        // 均值 (60+600)/2=330s → 中间档不动
        assertEquals(Duration.ZERO, PaceAdvisor.shift(listOf(Duration.ofSeconds(60), Duration.ofSeconds(600))))
    }
}
