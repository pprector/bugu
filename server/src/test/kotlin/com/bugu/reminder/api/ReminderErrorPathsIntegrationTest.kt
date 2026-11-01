package com.bugu.reminder.api

import com.bugu.bond.api.AuthTestClient
import com.bugu.identity.infra.ConsoleSmsSender
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.delete
import org.springframework.test.web.servlet.patch
import org.springframework.test.web.servlet.post
import org.springframework.test.web.servlet.put

/**
 * 提醒错误路径:不存在的提醒/确认/上报/撤销 → 404,状态冲突 → 409,非法参数 → 400。
 * 覆盖聚合前置校验的失败分支,保证错误码契约稳定。
 */
@SpringBootTest(properties = ["bugu.sms.provider=console", "bugu.push.provider=console"])
@AutoConfigureMockMvc
class ReminderErrorPathsIntegrationTest @Autowired constructor(
    private val mockMvc: MockMvc,
    private val smsSender: ConsoleSmsSender,
    private val jdbc: JdbcTemplate,
) {

    private val client by lazy { AuthTestClient(mockMvc, smsSender) }
    private val reminders by lazy { ReminderTestClient(mockMvc) }

    private fun fireIdOf(reminderId: Long): Long =
        jdbc.queryForObject(
            "SELECT id FROM reminder_fire WHERE reminder_id = ? ORDER BY id LIMIT 1",
            Long::class.java,
            reminderId,
        )!!

    private fun bondIdOf(aId: Long, bId: Long): Long =
        jdbc.queryForObject(
            "SELECT id FROM bond WHERE requester_id = ? AND receiver_id = ? AND status = 'ACTIVE' ORDER BY id DESC LIMIT 1",
            Long::class.java,
            aId,
            bId,
        )!!

    @Test
    fun `不存在的提醒_改取消确认上报撤销_404`() {
        val (a, _) = client.boundPair()

        mockMvc.patch("/api/v1/reminder/99999999") {
            header("Authorization", "Bearer ${a.token}")
            contentType = MediaType.APPLICATION_JSON
            content = """{"title": "改标题"}"""
        }.andExpect {
            status { isNotFound() }
            jsonPath("$.code") { value("NOT_FOUND") }
        }

        mockMvc.delete("/api/v1/reminder/99999999") {
            header("Authorization", "Bearer ${a.token}")
        }.andExpect {
            status { isNotFound() }
            jsonPath("$.code") { value("NOT_FOUND") }
        }

        mockMvc.post("/api/v1/reminder/ack") {
            header("Authorization", "Bearer ${a.token}")
            contentType = MediaType.APPLICATION_JSON
            content = """{"fireId": 99999999, "action": "DONE"}"""
        }.andExpect {
            status { isNotFound() }
            jsonPath("$.code") { value("NOT_FOUND") }
        }

        mockMvc.post("/api/v1/reminder/fire/99999999/report") {
            header("Authorization", "Bearer ${a.token}")
            contentType = MediaType.APPLICATION_JSON
            content = """{"idempotencyKey": "missing", "source": "LOCAL_ALARM"}"""
        }.andExpect {
            status { isNotFound() }
            jsonPath("$.code") { value("NOT_FOUND") }
        }

        mockMvc.post("/api/v1/reminder/ack/99999999/revoke") {
            header("Authorization", "Bearer ${a.token}")
        }.andExpect {
            status { isNotFound() }
            jsonPath("$.code") { value("NOT_FOUND") }
        }
    }

    @Test
    fun `已取消提醒_确认409`() {
        val (a, b) = client.boundPair()
        val reminderId = reminders.createAndGetId(a.token, b.id)
        val fireId = fireIdOf(reminderId)

        reminders.cancel(a.token, reminderId).andExpect { status { isOk() } }

        mockMvc.post("/api/v1/reminder/ack") {
            header("Authorization", "Bearer ${b.token}")
            contentType = MediaType.APPLICATION_JSON
            content = """{"fireId": $fireId, "action": "DONE"}"""
        }.andExpect {
            status { isConflict() }
            jsonPath("$.code") { value("CONFLICT") }
        }
    }

    @Test
    fun `解绑后修改提醒409`() {
        val (a, b) = client.boundPair()
        val reminderId = reminders.createAndGetId(a.token, b.id)
        val bondId = bondIdOf(a.id, b.id)

        mockMvc.post("/api/v1/bond/$bondId/unbind") {
            header("Authorization", "Bearer ${a.token}")
        }.andExpect { status { isOk() } }

        mockMvc.patch("/api/v1/reminder/$reminderId") {
            header("Authorization", "Bearer ${a.token}")
            contentType = MediaType.APPLICATION_JSON
            content = """{"title": "解绑后改名"}"""
        }.andExpect {
            status { isConflict() }
            jsonPath("$.code") { value("CONFLICT") }
        }
    }

    @Test
    fun `非法静音类型400`() {
        val (a, b) = client.boundPair()
        mockMvc.put("/api/v1/reminder/mute") {
            header("Authorization", "Bearer ${b.token}")
            contentType = MediaType.APPLICATION_JSON
            content = """{"creatorId": ${a.id}, "kind": "UNKNOWN"}"""
        }.andExpect {
            status { isBadRequest() }
            jsonPath("$.code") { value("INVALID_ARGUMENT") }
        }
    }

    @Test
    fun `创建时非法语气与时间400`() {
        val (a, b) = client.boundPair()

        mockMvc.post("/api/v1/reminder") {
            header("Authorization", "Bearer ${a.token}")
            contentType = MediaType.APPLICATION_JSON
            content = """{"receiverId": ${b.id}, "title": "语气非法", "kind": "ONCE", "tone": "SHOUT", "fireAt": "${reminders.futureFireAt()}"}"""
        }.andExpect {
            status { isBadRequest() }
            jsonPath("$.code") { value("INVALID_ARGUMENT") }
        }

        mockMvc.post("/api/v1/reminder") {
            header("Authorization", "Bearer ${a.token}")
            contentType = MediaType.APPLICATION_JSON
            content = """{"receiverId": ${b.id}, "title": "时间非法", "kind": "ONCE", "fireAt": "tomorrow"}"""
        }.andExpect {
            status { isBadRequest() }
            jsonPath("$.code") { value("INVALID_ARGUMENT") }
        }
    }

    @Test
    fun `撤销非已响应确认409`() {
        val (a, b) = client.boundPair()
        val reminderId = reminders.createAndGetId(a.token, b.id)
        val fireId = fireIdOf(reminderId)

        // fire 仍 PENDING 却存在 ack 行:撤销应拒绝(数据异常/时序防御)
        jdbc.update(
            "INSERT INTO reminder_ack (fire_id, action, acked_at) VALUES (?, 'DONE', ?)",
            fireId,
            java.time.LocalDateTime.now(),
        )
        val ackId = jdbc.queryForObject(
            "SELECT id FROM reminder_ack WHERE fire_id = ?",
            Long::class.java,
            fireId,
        )!!

        mockMvc.post("/api/v1/reminder/ack/$ackId/revoke") {
            header("Authorization", "Bearer ${a.token}")
        }.andExpect {
            status { isConflict() }
            jsonPath("$.code") { value("CONFLICT") }
        }

        // 清理:避免残留 ack 阻塞其他测试的 fire 清理
        jdbc.update("DELETE FROM reminder_ack WHERE fire_id = ?", fireId)
    }
}
