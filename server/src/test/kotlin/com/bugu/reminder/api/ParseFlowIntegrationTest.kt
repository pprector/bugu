package com.bugu.reminder.api

import com.bugu.bond.api.AuthTestClient
import com.bugu.identity.infra.ConsoleSmsSender
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.test.web.servlet.MockMvc
import com.bugu.ai.infra.AiGenLogRepository

/**
 * 一句话创建集成测试:parse 返回草稿/低置信回 needsManual、ai_gen_log 落库(scene=PARSE, degraded)、
 * 草稿确认经创建端点落库。
 */
@SpringBootTest(properties = ["bugu.sms.provider=console"])
@AutoConfigureMockMvc
class ParseFlowIntegrationTest @Autowired constructor(
    private val mockMvc: MockMvc,
    private val smsSender: ConsoleSmsSender,
    private val aiGenLogRepository: AiGenLogRepository,
) {

    private val client by lazy { AuthTestClient(mockMvc, smsSender) }
    private val reminders by lazy { ReminderTestClient(mockMvc) }

    @Test
    fun `明早九点提醒我妈吃降压药返回草稿且落ai_gen_log`() {
        val a = client.register()
        val before = aiGenLogRepository.findByScene("PARSE").size
        val body = reminders.parse(a.token, "明早九点提醒我妈吃降压药")
            .andExpect { status { isOk() } }
            .andReturn().response.contentAsString
        assertTrue(body.contains(""""needsManual":false"""))
        assertTrue(body.contains(""""fireAt":"${java.time.LocalDate.now().plusDays(1)}T09:00""""))
        assertTrue(body.contains(""""degraded":true"""))
        val logs = aiGenLogRepository.findByScene("PARSE")
        assertEquals(before + 1, logs.size)
        assertTrue(logs.last().degraded)
    }

    @Test
    fun `无意义输入回needsManual且不抛错`() {
        val a = client.register()
        reminders.parse(a.token, "asdfgh")
            .andExpect { status { isOk() } }
            .andExpect { jsonPath("$.data.needsManual") { value(true) } }
    }

    @Test
    fun `草稿字段确认后创建落库`() {
        val (a, b) = client.boundPair()
        val draft = reminders.parse(a.token, "明早九点提醒我妈吃降压药")
            .andReturn().response.contentAsString
        val fireAt = Regex(""""fireAt":"([^"]+)"""").find(draft)!!.groupValues[1]
        val title = Regex(""""title":"([^"]+)"""").find(draft)!!.groupValues[1]

        reminders.create(a.token, b.id, title = title, fireAt = fireAt)
            .andExpect { status { isOk() } }
    }
}
