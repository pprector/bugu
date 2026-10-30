package com.bugu.reminder.api

import com.bugu.ai.infra.AiGenLogRepository
import com.bugu.ai.infra.LlmCopyResult
import com.bugu.ai.infra.LlmGateway
import com.bugu.bond.api.AuthTestClient
import com.bugu.identity.infra.ConsoleSmsSender
import com.bugu.push.infra.CopyTemplateRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.context.annotation.Bean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get

/**
 * AI 叫醒语每日生成与缓存(E5-S3):假网关可开关失败模拟降级。
 * 同日幂等命中只生成一次;降级结果也落缓存;非互叫 400、第三人 403。
 */
@SpringBootTest(properties = ["bugu.sms.provider=console", "bugu.llm.provider=fake"])
@AutoConfigureMockMvc
class WakeupCopyIntegrationTest @Autowired constructor(
    private val mockMvc: MockMvc,
    private val smsSender: ConsoleSmsSender,
    private val aiGenLogRepository: AiGenLogRepository,
    private val copyTemplateRepository: CopyTemplateRepository,
    private val fakeGateway: FakeGateway,
) {

    @TestConfiguration
    class TestBeans {
        @Bean
        fun fakeGateway(): FakeGateway = FakeGateway()
    }

    class FakeGateway : LlmGateway {
        var fail: Boolean = false
        var callCount: Int = 0
        var reply: String = "宝贝,太阳晒屁股啦,起床咯~"

        override fun generateText(systemPrompt: String, userPrompt: String, maxTokens: Int, temperature: Double): LlmCopyResult =
            generateCopy(userPrompt)

        override fun generateCopy(prompt: String): LlmCopyResult {
            callCount++
            if (fail) throw IllegalStateException("llm down")
            return LlmCopyResult(reply, 12, 6)
        }

        fun reset() {
            fail = false
            callCount = 0
        }
    }

    private val client by lazy { AuthTestClient(mockMvc, smsSender) }
    private val reminders by lazy { ReminderTestClient(mockMvc) }

    @BeforeEach
    fun clean() {
        fakeGateway.reset()
        aiGenLogRepository.deleteAll(aiGenLogRepository.findByScene("WAKEUP"))
    }

    private fun wakeupCopy(token: String, reminderId: Long, date: String? = null) =
        mockMvc.get("/api/v1/reminder/$reminderId/wakeup-copy" + (date?.let { "?date=$it" } ?: "")) {
            header("Authorization", "Bearer $token")
        }

    @Test
    fun `网关失败时走WAKEUP模板且降级落库`() {
        val (a, b) = client.boundPair()
        val reminderId = reminders.createAndGetId(a.token, b.id, "互叫起床", kind = "MUTUAL_ALARM")
        fakeGateway.fail = true

        val body = wakeupCopy(a.token, reminderId).andExpect {
            status { isOk() }
            jsonPath("$.data.degraded") { value(true) }
        }.andReturn().response.contentAsString
        val content = Regex(""""content"\s*:\s*"([^"]+)"""").find(body)!!.groupValues[1]
        val templates = copyTemplateRepository.findByToneAndSceneAndEnabledTrue("GENTLE", "WAKEUP").map { it.content }
        assertTrue(content in templates, "降级内容应来自 WAKEUP 模板池")

        val logs = aiGenLogRepository.findByScene("WAKEUP")
        assertEquals(1, logs.size)
        assertEquals(true, logs[0].degraded)
    }

    @Test
    fun `同日两次请求内容一致且只生成一次`() {
        val (a, b) = client.boundPair()
        val reminderId = reminders.createAndGetId(a.token, b.id, "互叫起床", kind = "MUTUAL_ALARM")
        // 创建后 T0 推送也经同一假网关,剔除其计数后再观察叫醒语生成次数
        val afterCreate = fakeGateway.callCount

        // 双方拉取同一 reminder 同一日,命中同一缓存
        wakeupCopy(a.token, reminderId).andExpect {
            status { isOk() }
            jsonPath("$.data.content") { value("宝贝,太阳晒屁股啦,起床咯~") }
            jsonPath("$.data.degraded") { value(false) }
        }
        wakeupCopy(b.token, reminderId).andExpect {
            status { isOk() }
            jsonPath("$.data.content") { value("宝贝,太阳晒屁股啦,起床咯~") }
            jsonPath("$.data.degraded") { value(false) }
        }

        assertEquals(afterCreate + 1, fakeGateway.callCount, "同日第二次应命中缓存")
        val logs = aiGenLogRepository.findByScene("WAKEUP")
        assertEquals(1, logs.size)
        assertEquals(false, logs[0].degraded)
        assertEquals(12, logs[0].promptTokens)

        // 显式 date 次日预取:合法日期参数可拉取(独立缓存键,新生成一条)
        wakeupCopy(a.token, reminderId, date = "2099-01-01").andExpect {
            status { isOk() }
            jsonPath("$.data.date") { value("2099-01-01") }
            jsonPath("$.data.degraded") { value(false) }
        }
    }

    @Test
    fun `网关返回空文案时降级WAKEUP模板`() {
        val (a, b) = client.boundPair()
        val reminderId = reminders.createAndGetId(a.token, b.id, "互叫起床", kind = "MUTUAL_ALARM")
        fakeGateway.reply = "   "

        wakeupCopy(a.token, reminderId).andExpect {
            status { isOk() }
            jsonPath("$.data.degraded") { value(true) }
        }
        val logs = aiGenLogRepository.findByScene("WAKEUP")
        assertEquals(1, logs.size)
        assertEquals(true, logs[0].degraded)
    }

    @Test
    fun `三档WAKEUP模板各至少5条`() {
        for (tone in listOf("GENTLE", "NAG", "STRICT")) {
            val templates = copyTemplateRepository.findByToneAndSceneAndEnabledTrue(tone, "WAKEUP")
            assertTrue(templates.size >= 5, "tone=$tone WAKEUP 模板应 ≥5 条,实际 ${templates.size}")
            assertTrue(templates.all { it.content.length <= 40 }, "tone=$tone 模板应 ≤40 字")
        }
    }

    @Test
    fun `非互叫与第三人均被拒`() {
        val (a, b) = client.boundPair()
        val onceId = reminders.createAndGetId(a.token, b.id)
        wakeupCopy(a.token, onceId).andExpect { status { isBadRequest() } }

        val mutualId = reminders.createAndGetId(a.token, b.id, "互叫起床", kind = "MUTUAL_ALARM")
        val outsider = client.register()
        wakeupCopy(outsider.token, mutualId).andExpect { status { isForbidden() } }
        wakeupCopy(outsider.token, 999999).andExpect { status { isNotFound() } }

        // 非法日期格式 400
        wakeupCopy(a.token, mutualId, date = "2026/01/01").andExpect { status { isBadRequest() } }
    }
}
