package com.bugu.reminder.api

import com.bugu.bond.api.AuthTestClient
import com.bugu.identity.infra.ConsoleSmsSender
import com.bugu.reminder.infra.ReminderFireRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.test.web.servlet.MockMvc
import java.time.LocalDateTime

/**
 * reminder 模块端到端集成测试(S5):注册 → 绑定 → 静音拦截 → 恢复 → 一句话解析草稿
 * → 创建(双方列表可见)→ PATCH 修改(标题/fireAt 同步)→ DELETE 取消(CANCELED)→ 当日上限静默。
 */
@SpringBootTest(properties = ["bugu.sms.provider=console"])
@AutoConfigureMockMvc
class ReminderE2EIntegrationTest @Autowired constructor(
    private val mockMvc: MockMvc,
    private val smsSender: ConsoleSmsSender,
    private val reminderFireRepository: ReminderFireRepository,
) {

    private val client by lazy { AuthTestClient(mockMvc, smsSender) }
    private val reminders by lazy { ReminderTestClient(mockMvc) }

    @Test
    fun `提醒全链路_静音拦截解析创建修改取消与上限静默`() {
        val (a, b) = client.boundPair()

        // 静音拦截:命中 REMINDER_MUTED 不落库,恢复后正常
        reminders.mute(b.token, a.id, "ONCE").andExpect { status { isOk() } }
        reminders.create(a.token, b.id)
            .andExpect { status { isBadRequest() } }
            .andExpect { jsonPath("$.code") { value("REMINDER_MUTED") } }
        reminders.unmute(b.token, a.id, "ONCE").andExpect { status { isOk() } }

        // 一句话解析草稿 → 确认创建
        val draft = reminders.parse(a.token, "明早九点提醒我吃药")
            .andExpect { status { isOk() } }.andReturn().response.contentAsString
        assertTrue(draft.contains(""""needsManual":false"""))
        val parsedFireAt = Regex(""""fireAt":"([^"]+)"""").find(draft)!!.groupValues[1]
        val parsedTitle = Regex(""""title":"([^"]+)"""").find(draft)!!.groupValues[1]
        val reminderId = reminders.createAndGetId(a.token, b.id, title = parsedTitle)

        // 双方列表可见
        assertTrue(reminders.mine(a.token).contains(""""id":$reminderId"""))
        assertTrue(reminders.incoming(b.token).contains(parsedTitle))

        // PATCH 修改标题与触发时刻,fire 记录同步
        val newFireAt = LocalDateTime.now().plusHours(6).withNano(0)
        reminders.patch(a.token, reminderId, """{"title": "改后吃药", "fireAt": "$newFireAt"}""")
            .andExpect { status { isOk() } }
        val fires = reminderFireRepository.findByReminderIdOrderByFireAt(reminderId)
        assertEquals(1, fires.size)
        assertEquals(newFireAt, fires[0].fireAt)
        assertTrue(reminders.mine(a.token).contains("改后吃药"))

        // DELETE 取消 → CANCELED
        reminders.cancel(a.token, reminderId)
            .andExpect { status { isOk() } }
            .andExpect { jsonPath("$.data.status") { value("CANCELED") } }

        // 当日上限:已建 1 条(取消仍计数),再建 9 条正常,第 11 条 silent=true
        repeat(9) { reminders.create(a.token, b.id, title = "补${it + 1}").andExpect { status { isOk() } } }
        val eleventh = reminders.create(a.token, b.id, title = "第十一条")
            .andExpect { status { isOk() } }.andReturn().response.contentAsString
        assertTrue(eleventh.contains(""""silent":true"""))
    }
}
