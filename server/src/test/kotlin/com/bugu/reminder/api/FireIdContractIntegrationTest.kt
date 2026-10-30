package com.bugu.reminder.api

import com.bugu.bond.api.AuthTestClient
import com.bugu.identity.infra.ConsoleSmsSender
import com.bugu.reminder.infra.ReminderFireRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post

/**
 * fireId 契约测试(E8 S3 前置):列表与 current 视图附「查看者可操作的待响应 fire」,
 * 客户端据此完成 ack/report 闭环(此前 fireId 仅服务端可见,契约断裂)。
 */
@SpringBootTest(properties = ["bugu.sms.provider=console"])
@AutoConfigureMockMvc
class FireIdContractIntegrationTest @Autowired constructor(
    private val mockMvc: MockMvc,
    private val smsSender: ConsoleSmsSender,
    private val reminderFireRepository: ReminderFireRepository,
) {

    private val client by lazy { AuthTestClient(mockMvc, smsSender) }
    private val reminders by lazy { ReminderTestClient(mockMvc) }

    private fun mine(token: String): String = mockMvc.get("/api/v1/reminder/mine") {
        header("Authorization", "Bearer $token")
    }.andExpect { status { isOk() } }.andReturn().response.contentAsString

    private fun incoming(token: String): String = mockMvc.get("/api/v1/reminder/incoming") {
        header("Authorization", "Bearer $token")
    }.andExpect { status { isOk() } }.andReturn().response.contentAsString

    /** explicitNulls=false 时 null fireId 字段整体省略,用 Jackson 按缺失=null 归一。 */
    private val mapper = com.fasterxml.jackson.databind.ObjectMapper()

    private fun fireIds(body: String): List<Long?> {
        val data = mapper.readTree(body).get("data") ?: return emptyList()
        if (!data.isArray) {
            return listOf(if (data.hasNonNull("fireId")) data.get("fireId").asLong() else null)
        }
        return data.map { if (it.hasNonNull("fireId")) it.get("fireId").asLong() else null }
    }

    @Test
    fun `ONCE_接收人视图带fireId_发起人视图fireId为null`() {
        val (a, b) = client.boundPair()
        val reminderId = reminders.createAndGetId(a.token, b.id)
        val expectedFireId = reminderFireRepository.findByReminderIdOrderByFireAt(reminderId).first().id

        // 接收人 incoming:fire 目标是自己,可操作
        val incomingIds = fireIds(incoming(b.token))
        assertEquals(listOf(expectedFireId), incomingIds)

        // 发起人 mine:fire 目标是接收人,自己无可操作 fire
        val mineIds = fireIds(mine(a.token))
        assertEquals(listOf<Long?>(null), mineIds)
    }

    @Test
    fun `确认后fireId消失_回到null`() {
        val (a, b) = client.boundPair()
        val reminderId = reminders.createAndGetId(a.token, b.id)
        val fireId = reminderFireRepository.findByReminderIdOrderByFireAt(reminderId).first().id!!

        mockMvc.post("/api/v1/reminder/ack") {
            header("Authorization", "Bearer ${b.token}")
            contentType = MediaType.APPLICATION_JSON
            content = """{"fireId": $fireId, "action": "DONE"}"""
        }.andExpect { status { isOk() } }

        assertEquals(listOf<Long?>(null), fireIds(incoming(b.token)))
    }

    @Test
    fun `MUTUAL_双方视图各带自己的fireId`() {
        val (a, b) = client.boundPair()
        val reminderId = reminders.createAndGetId(a.token, b.id, kind = "MUTUAL_ALARM")
        val fires = reminderFireRepository.findByReminderIdOrderByFireAt(reminderId)
        val fireA = fires.first { it.targetUserId == a.id }.id
        val fireB = fires.first { it.targetUserId == b.id }.id

        assertEquals(listOf(fireA), fireIds(mine(a.token)))
        assertEquals(listOf(fireB), fireIds(incoming(b.token)))
    }

    @Test
    fun `current_长辈视图带fireId_无待处理时data省略`() {
        val (a, b) = client.boundPair()
        val reminderId = reminders.createAndGetId(a.token, b.id)
        val expectedFireId = reminderFireRepository.findByReminderIdOrderByFireAt(reminderId).first().id

        val body = mockMvc.get("/api/v1/reminder/current") {
            header("Authorization", "Bearer ${b.token}")
        }.andExpect { status { isOk() } }.andReturn().response.contentAsString
        assertEquals(listOf(expectedFireId), fireIds(body))
    }

    @Test
    fun `ACKED后mine视图带ackId_供撤销误触`() {
        val (a, b) = client.boundPair()
        val reminderId = reminders.createAndGetId(a.token, b.id)
        val fireId = reminderFireRepository.findByReminderIdOrderByFireAt(reminderId).first().id!!

        // 待响应时无 ackId
        assertNull(ackIds(mine(a.token)).first())

        mockMvc.post("/api/v1/reminder/ack") {
            header("Authorization", "Bearer ${b.token}")
            contentType = MediaType.APPLICATION_JSON
            content = """{"fireId": $fireId, "action": "DONE"}"""
        }.andExpect { status { isOk() } }

        val acks = ackIds(mine(a.token))
        assertEquals(1, acks.size)
        val ackId = requireNotNull(acks.first()) { "ACKED 后 mine 视图应带 ackId" }

        // 撤销误触可通(E6-S2):ackId 契约闭环
        mockMvc.post("/api/v1/reminder/ack/$ackId/revoke") {
            header("Authorization", "Bearer ${a.token}")
        }.andExpect { status { isOk() } }

        // 撤销后 reminder 回 FIRED,ackId 消失
        assertNull(ackIds(mine(a.token)).first())
    }

    /** explicitNulls=false 时 null ackId 字段整体省略,按缺失=null 归一。 */
    private fun ackIds(body: String): List<Long?> {
        val data = mapper.readTree(body).get("data") ?: return emptyList()
        if (!data.isArray) {
            return listOf(if (data.hasNonNull("ackId")) data.get("ackId").asLong() else null)
        }
        return data.map { if (it.hasNonNull("ackId")) it.get("ackId").asLong() else null }
    }
}
