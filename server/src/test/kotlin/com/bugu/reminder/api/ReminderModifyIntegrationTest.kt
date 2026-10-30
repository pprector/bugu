package com.bugu.reminder.api

import com.bugu.bond.api.AuthTestClient
import com.bugu.identity.infra.ConsoleSmsSender
import com.bugu.reminder.infra.ReminderFireRepository
import com.bugu.reminder.infra.ReminderRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.test.web.servlet.MockMvc
import java.time.LocalDateTime

/**
 * 提醒修改/取消与每日上限集成测试(S4):当日第 11 条静默落库、PATCH 标题/fireAt 与 fire 同步、
 * PENDING 取消、终态 409、接收人越权 403。账号经验证码登录流程随机注册。
 */
@SpringBootTest(properties = ["bugu.sms.provider=console"])
@AutoConfigureMockMvc
class ReminderModifyIntegrationTest @Autowired constructor(
    private val mockMvc: MockMvc,
    private val smsSender: ConsoleSmsSender,
    private val reminderRepository: ReminderRepository,
    private val reminderFireRepository: ReminderFireRepository,
) {

    private val client by lazy { AuthTestClient(mockMvc, smsSender) }
    private val reminders by lazy { ReminderTestClient(mockMvc) }

    @Test
    fun `当日第11条创建silent为true且仍200`() {
        val (a, b) = client.boundPair()
        repeat(10) { reminders.create(a.token, b.id, title = "第${it + 1}条").andExpect { status { isOk() } } }

        val body = reminders.create(a.token, b.id, title = "第十一条")
            .andExpect { status { isOk() } }.andReturn().response.contentAsString
        assertTrue(body.contains(""""silent":true"""))
        assertTrue(reminders.mine(a.token).contains(""""silent":true"""))
    }

    @Test
    fun `修改标题与触发时刻_双方可见且fire同步`() {
        val (a, b) = client.boundPair()
        val reminderId = reminders.createAndGetId(a.token, b.id)
        val newFireAt = LocalDateTime.now().plusHours(5).withNano(0)

        reminders.patch(a.token, reminderId, """{"title": "改后标题", "fireAt": "$newFireAt"}""")
            .andExpect { status { isOk() } }

        val fires = reminderFireRepository.findByReminderIdOrderByFireAt(reminderId)
        assertEquals(1, fires.size)
        assertEquals(newFireAt, fires[0].fireAt)
        assertEquals("$reminderId:$newFireAt", fires[0].idempotencyKey)

        assertTrue(reminders.mine(a.token).contains("改后标题"))
        assertTrue(reminders.incoming(b.token).contains("改后标题"))
    }

    @Test
    fun `取消PENDING提醒后状态为CANCELED`() {
        val (a, b) = client.boundPair()
        val reminderId = reminders.createAndGetId(a.token, b.id)

        val body = reminders.cancel(a.token, reminderId)
            .andExpect { status { isOk() } }.andReturn().response.contentAsString
        assertTrue(body.contains(""""status":"CANCELED"""))
        assertTrue(reminders.mine(a.token).contains(""""status":"CANCELED"""))
    }

    @Test
    fun `非待触发状态修改与取消均409`() {
        val (a, b) = client.boundPair()
        val reminderId = reminders.createAndGetId(a.token, b.id)
        val row = reminderRepository.findById(reminderId).get()
        reminderRepository.save(row.copy(status = "ACKED"))

        reminders.cancel(a.token, reminderId).andExpect { status { isConflict() } }
        reminders.patch(a.token, reminderId, """{"title": "改不动"}""").andExpect { status { isConflict() } }
    }

    @Test
    fun `接收人修改或取消返回403`() {
        val (a, b) = client.boundPair()
        val reminderId = reminders.createAndGetId(a.token, b.id)

        reminders.patch(b.token, reminderId, """{"title": "越权改"}""").andExpect { status { isForbidden() } }
        reminders.cancel(b.token, reminderId).andExpect { status { isForbidden() } }
    }

    @Test
    fun `修改字段校验失败均400`() {
        val (a, b) = client.boundPair()
        val reminderId = reminders.createAndGetId(a.token, b.id)

        reminders.patch(a.token, reminderId, """{}""").andExpect { status { isBadRequest() } }
        reminders.patch(a.token, reminderId, """{"title": ""}""").andExpect { status { isBadRequest() } }
        reminders.patch(a.token, reminderId, """{"title": "这是一个超过二十个字的提醒标题不应该通过校验"}""")
            .andExpect { status { isBadRequest() } }
        reminders.patch(a.token, reminderId, """{"fireAt": "not-a-time"}""").andExpect { status { isBadRequest() } }
        reminders.patch(a.token, reminderId, """{"fireAt": "${LocalDateTime.now().minusHours(1).withNano(0)}"}""")
            .andExpect { status { isBadRequest() } }
    }
}
