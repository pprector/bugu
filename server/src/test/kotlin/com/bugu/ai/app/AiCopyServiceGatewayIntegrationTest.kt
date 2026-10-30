package com.bugu.ai.app

import com.bugu.ai.domain.CopyContext
import com.bugu.ai.domain.DayPart
import com.bugu.ai.domain.ResponseHabit
import com.bugu.ai.infra.AiGenLogRepository
import com.bugu.ai.infra.LlmCopyResult
import com.bugu.ai.infra.LlmGateway
import com.bugu.ai.infra.LlmUnavailableException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean

/**
 * 有网关路径:@TestConfiguration 注入行为可变的假 LlmGateway。
 * 正常生成落 degraded=false + tokens;>40 字截断;空串与异常降级模板。
 */
@SpringBootTest(properties = ["bugu.sms.provider=console", "bugu.llm.provider=fake"])
class AiCopyServiceGatewayIntegrationTest @Autowired constructor(
    private val aiCopyService: AiCopyService,
    private val aiGenLogRepository: AiGenLogRepository,
    private val fakeGateway: FakeGateway,
) {

    @TestConfiguration
    class FakeGatewayConfig {
        /** FakeGateway 本身即 LlmGateway,只注册一个 bean 避免 ObjectProvider 取到多个。 */
        @Bean
        fun fakeGateway(): FakeGateway = FakeGateway()
    }

    /** 行为可变的假网关:lastPrompt 记录入参供脱敏断言。 */
    class FakeGateway : LlmGateway {
        var behavior: () -> LlmCopyResult = { LlmCopyResult("记得吃药哦,我陪着你~", 30, 12) }
        var lastPrompt: String? = null

        override fun generateText(systemPrompt: String, userPrompt: String, maxTokens: Int, temperature: Double): LlmCopyResult =
            generateCopy(userPrompt)

        override fun generateCopy(prompt: String): LlmCopyResult {
            lastPrompt = prompt
            return behavior()
        }

        fun reset() {
            behavior = { LlmCopyResult("记得吃药哦,我陪着你~", 30, 12) }
            lastPrompt = null
        }
    }

    @BeforeEach
    fun clean() {
        aiGenLogRepository.deleteAll(aiGenLogRepository.findByScene("COPY"))
        fakeGateway.reset()
    }

    private fun ctx() = CopyContext(
        title = "吃降压药", scene = "REPUSH", tone = "GENTLE",
        attempt = 2, responseHabit = ResponseHabit.FAST, dayPart = DayPart.MORNING,
    )

    @Test
    fun `正常生成返回文案且落tokens与degraded_false`() {
        val copy = aiCopyService.compose("REPUSH", "GENTLE", ctx())
        assertEquals("记得吃药哦,我陪着你~", copy)

        val logs = aiGenLogRepository.findByScene("COPY")
        assertEquals(1, logs.size)
        assertEquals(false, logs[0].degraded)
        assertEquals(30, logs[0].promptTokens)
        assertEquals(12, logs[0].outputTokens)
        // 提示词只含脱敏要素
        val prompt = fakeGateway.lastPrompt!!
        assertTrue(prompt.contains("吃降压药"))
        assertTrue(prompt.contains("第 2 次提醒"))
    }

    @Test
    fun `超过40字的生成结果被截断`() {
        fakeGateway.behavior = { LlmCopyResult("长".repeat(60), 1, 1) }
        val copy = aiCopyService.compose("REPUSH", "GENTLE", ctx())
        assertEquals(40, copy!!.length)
    }

    @Test
    fun `空文案降级模板`() {
        fakeGateway.behavior = { LlmCopyResult("   ", 1, 1) }
        val copy = aiCopyService.compose("REPUSH", "GENTLE", ctx())
        assertTrue(copy != null && !copy.contains("   "))

        val logs = aiGenLogRepository.findByScene("COPY")
        assertEquals(1, logs.size)
        assertEquals(true, logs[0].degraded)
    }

    @Test
    fun `网关异常降级模板`() {
        fakeGateway.behavior = { throw LlmUnavailableException("超时") }
        val copy = aiCopyService.compose("REPUSH", "GENTLE", ctx())
        assertTrue(copy != null)

        val logs = aiGenLogRepository.findByScene("COPY")
        assertEquals(1, logs.size)
        assertEquals(true, logs[0].degraded)
    }
}
