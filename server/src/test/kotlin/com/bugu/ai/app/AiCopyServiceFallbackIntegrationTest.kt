package com.bugu.ai.app

import com.bugu.ai.domain.CopyContext
import com.bugu.ai.domain.DayPart
import com.bugu.ai.domain.ResponseHabit
import com.bugu.ai.infra.AiGenLogRepository
import com.bugu.push.infra.CopyTemplateRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest

/**
 * 无网关降级路径(provider=none 默认):compose 走 copy_template 模板,
 * ai_gen_log 落 scene=COPY degraded=true;三档 REPUSH 模板各 ≥5 条(V9+V10 种子)。
 */
@SpringBootTest(properties = ["bugu.sms.provider=console", "bugu.llm.provider=none"])
class AiCopyServiceFallbackIntegrationTest @Autowired constructor(
    private val aiCopyService: AiCopyService,
    private val aiGenLogRepository: AiGenLogRepository,
    private val copyTemplateRepository: CopyTemplateRepository,
) {

    @BeforeEach
    fun clean() {
        aiGenLogRepository.deleteAll(aiGenLogRepository.findByScene("COPY"))
    }

    private fun ctx(tone: String = "GENTLE") = CopyContext(
        title = "吃降压药", scene = "REPUSH", tone = tone,
        attempt = 2, responseHabit = ResponseHabit.UNKNOWN, dayPart = DayPart.MORNING,
    )

    @Test
    fun `无网关时走模板且落degraded日志`() {
        val copy = aiCopyService.compose("REPUSH", "GENTLE", ctx())
        assertNotNull(copy)
        val templates = copyTemplateRepository.findByToneAndSceneAndEnabledTrue("GENTLE", "REPUSH")
            .map { it.content }
        assertTrue(copy in templates)

        val logs = aiGenLogRepository.findByScene("COPY")
        assertEquals(1, logs.size)
        assertEquals(true, logs[0].degraded)
        assertEquals("GENTLE", logs[0].tone)
        assertNotNull(logs[0].latencyMs)
    }

    @Test
    fun `三档REPUSH模板各至少5条`() {
        for (tone in listOf("GENTLE", "NAG", "STRICT")) {
            val templates = copyTemplateRepository.findByToneAndSceneAndEnabledTrue(tone, "REPUSH")
            assertTrue(templates.size >= 5, "tone=$tone 仅 ${templates.size} 条")
        }
    }

    @Test
    fun `模板随机轮换可命中不同文案`() {
        // 抽 30 次应命中至少 2 条不同模板(5 条轮换,单次概率 1/5,30 次不中的概率可忽略)
        val hits = (1..30).mapNotNull { aiCopyService.compose("REPUSH", "NAG", ctx("NAG")) }.toSet()
        assertTrue(hits.size >= 2, "30 次抽取只命中 $hits")
    }
}
