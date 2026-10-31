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
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import java.time.LocalDateTime

/**
 * 提醒创建集成测试:绑定双方创建主路径(双方可见 + fire 预生成)、字段校验 400、
 * 无 bond 403、解绑后列表隔离。账号经验证码登录流程随机注册。
 */
@SpringBootTest(properties = ["bugu.sms.provider=console"])
@AutoConfigureMockMvc
class ReminderFlowIntegrationTest @Autowired constructor(
    private val mockMvc: MockMvc,
    private val smsSender: ConsoleSmsSender,
    private val reminderFireRepository: ReminderFireRepository,
) {

    private val client by lazy { AuthTestClient(mockMvc, smsSender) }
    private val reminders by lazy { ReminderTestClient(mockMvc) }

    @Test
    fun `创建主路径_双方可见且首条fire预生成`() {
        val (a, b) = client.boundPair()
        val body = reminders.create(a.token, b.id, note = "饭后服用")
            .andExpect { status { isOk() } }
            .andReturn().response.contentAsString
        val reminderId = Regex(""""id"\s*:\s*(\d+)""").find(body)!!.groupValues[1].toLong()

        val fires = reminderFireRepository.findByReminderIdOrderByFireAt(reminderId)
        assertEquals(1, fires.size)
        assertEquals("PENDING", fires[0].state)
        assertTrue(fires[0].idempotencyKey.startsWith("$reminderId:"))

        assertTrue(reminders.mine(a.token).contains(""""id":$reminderId"""))
        assertTrue(reminders.incoming(b.token).contains(""""id":$reminderId"""))
    }

    @Test
    fun `多人创建为每位接收人分别生成提醒`() {
        val (a, b) = client.boundPair()
        val c = client.register()
        client.confirm(c.token, client.initiateAndGetCode(a.token))
        mockMvc.post("/api/v1/reminder/batch") {
            header("Authorization", "Bearer ${a.token}")
            contentType = org.springframework.http.MediaType.APPLICATION_JSON
            content = """{"receiverIds":[${b.id},${c.id}],"title":"一起喝水","kind":"ONCE","fireAt":"${LocalDateTime.now().plusHours(2).withNano(0)}"}"""
        }.andExpect { status { isOk() } }
        assertTrue(reminders.incoming(b.token).contains("一起喝水"))
        assertTrue(reminders.incoming(c.token).contains("一起喝水"))
    }

    @Test
    fun `批量创建不允许空接收人`() {
        val a = client.register()
        mockMvc.post("/api/v1/reminder/batch") {
            header("Authorization", "Bearer ${a.token}")
            contentType = org.springframework.http.MediaType.APPLICATION_JSON
            content = """{"receiverIds":[],"title":"空接收人","kind":"ONCE","fireAt":"${LocalDateTime.now().plusHours(2).withNano(0)}"}"""
        }.andExpect { status { isBadRequest() } }
    }

    @Test
    fun `批量创建最多允许二十位接收人`() {
        val a = client.register()
        val receiverIds = (1L..21L).joinToString(",")
        mockMvc.post("/api/v1/reminder/batch") {
            header("Authorization", "Bearer ${a.token}")
            contentType = org.springframework.http.MediaType.APPLICATION_JSON
            content = """{"receiverIds":[$receiverIds],"title":"人数上限","kind":"ONCE","fireAt":"${LocalDateTime.now().plusHours(2).withNano(0)}"}"""
        }.andExpect { status { isBadRequest() } }
    }

    @Test
    fun `批量创建互叫闹钟只允许一位接收人`() {
        val (a, b) = client.boundPair()
        val c = client.register()
        client.confirm(c.token, client.initiateAndGetCode(a.token))
        mockMvc.post("/api/v1/reminder/batch") {
            header("Authorization", "Bearer ${a.token}")
            contentType = org.springframework.http.MediaType.APPLICATION_JSON
            content = """{"receiverIds":[${b.id},${c.id}],"title":"一起起床","kind":"MUTUAL_ALARM","fireAt":"${LocalDateTime.now().plusHours(2).withNano(0)}"}"""
        }.andExpect { status { isBadRequest() } }
    }

    @Test
    fun `批量创建任一接收人失败时整体回滚`() {
        val (a, b) = client.boundPair()
        val c = client.register()
        mockMvc.post("/api/v1/reminder/batch") {
            header("Authorization", "Bearer ${a.token}")
            contentType = org.springframework.http.MediaType.APPLICATION_JSON
            content = """{"receiverIds":[${b.id},${c.id}],"title":"原子批量","kind":"ONCE","fireAt":"${LocalDateTime.now().plusHours(2).withNano(0)}"}"""
        }.andExpect { status { isForbidden() } }
        assertTrue(!reminders.incoming(b.token).contains("原子批量"))
    }

    @Test
    fun `REPEAT类型带DAILY规则创建成功`() {
        val (a, b) = client.boundPair()
        reminders.create(a.token, b.id, kind = "REPEAT", repeatRule = "DAILY")
            .andExpect { status { isOk() } }
    }

    @Test
    fun `互叫闹钟可开语音`() {
        val (a, b) = client.boundPair()
        reminders.create(a.token, b.id, kind = "MUTUAL_ALARM", voiceEnabled = true)
            .andExpect { status { isOk() } }
    }

    @Test
    fun `自惦记_送自己无需bond且mine可见incoming不含`() {
        val a = client.register()
        val body = reminders.create(a.token, a.id, title = "提醒我自己喝水")
            .andExpect { status { isOk() } }
            .andReturn().response.contentAsString
        val reminderId = Regex(""""id"\s*:\s*(\d+)""").find(body)!!.groupValues[1].toLong()
        // explicitNulls=false:null 字段直接省略,断言不含 bondId 数值(未挂互伴)
        assertTrue(!body.contains(Regex(""""bondId"\s*:\s*\d+""")))

        assertTrue(reminders.mine(a.token).contains(""""id":$reminderId"""))
        // 「我接收的」语义=别人发给我的,自惦记不进 incoming
        assertTrue(!reminders.incoming(a.token).contains(""""id":$reminderId"""))
    }

    @Test
    fun `自惦记禁用互叫闹钟400`() {
        val a = client.register()
        reminders.create(a.token, a.id, kind = "MUTUAL_ALARM", voiceEnabled = true)
            .andExpect { status { isBadRequest() } }
    }

    @Test
    fun `字段校验失败均400`() {
        val (a, b) = client.boundPair()
        reminders.create(a.token, b.id, title = "这是一个超过二十个字的提醒标题不应该通过校验")
            .andExpect { status { isBadRequest() } }
        reminders.create(a.token, b.id, title = "")
            .andExpect { status { isBadRequest() } }
        reminders.create(a.token, b.id, fireAt = LocalDateTime.now().minusHours(1).withNano(0).toString())
            .andExpect { status { isBadRequest() } }
        reminders.create(a.token, b.id, kind = "REPEAT")
            .andExpect { status { isBadRequest() } }
        reminders.create(a.token, b.id, kind = "REPEAT", repeatRule = "HOURLY:1")
            .andExpect { status { isBadRequest() } }
        reminders.create(a.token, b.id, kind = "ONCE", repeatRule = "DAILY")
            .andExpect { status { isBadRequest() } }
        reminders.create(a.token, b.id, kind = "ONCE", voiceEnabled = true)
            .andExpect { status { isBadRequest() } }
        reminders.create(a.token, b.id, kind = "UNKNOWN")
            .andExpect { status { isBadRequest() } }
        reminders.create(a.token, b.id, note = "长".repeat(51))
            .andExpect { status { isBadRequest() } }
    }

    @Test
    fun `无ACTIVE绑定创建返回403`() {
        val a = client.register()
        val c = client.register()
        reminders.create(a.token, c.id)
            .andExpect { status { isForbidden() } }
    }

    @Test
    fun `解绑后双方列表即时隔离`() {
        val (a, b) = client.boundPair()
        val reminderId = reminders.createAndGetId(a.token, b.id)

        val bonds = mockMvc.get("/api/v1/bond") { header("Authorization", "Bearer ${a.token}") }
            .andReturn().response.contentAsString
        val bondId = Regex(""""id"\s*:\s*(\d+)""").find(bonds)!!.groupValues[1].toLong()
        mockMvc.post("/api/v1/bond/$bondId/unbind") { header("Authorization", "Bearer ${a.token}") }
            .andExpect { status { isOk() } }

        assertTrue(!reminders.mine(a.token).contains(""""id":$reminderId"""))
        assertTrue(!reminders.incoming(b.token).contains(""""id":$reminderId"""))
    }
}
