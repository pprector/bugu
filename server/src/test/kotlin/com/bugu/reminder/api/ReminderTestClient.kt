package com.bugu.reminder.api

import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.delete
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.patch
import org.springframework.test.web.servlet.post
import org.springframework.test.web.servlet.put
import java.time.LocalDateTime

/** reminder 集成测试共用客户端:创建/修改/取消/双方列表/静音/一句话解析。 */
class ReminderTestClient(private val mockMvc: MockMvc) {

    fun futureFireAt(hours: Long = 2): String = LocalDateTime.now().plusHours(hours).withNano(0).toString()

    fun create(
        token: String,
        receiverId: Long,
        title: String = "吃降压药",
        kind: String = "ONCE",
        fireAt: String = futureFireAt(),
        repeatRule: String? = null,
        voiceEnabled: Boolean = false,
        note: String? = null,
    ) = mockMvc.post("/api/v1/reminder") {
        header("Authorization", "Bearer $token")
        contentType = MediaType.APPLICATION_JSON
        content = buildString {
            append("""{"receiverId": $receiverId, "title": "$title", "kind": "$kind", "fireAt": "$fireAt"""")
            if (repeatRule != null) append(""", "repeatRule": "$repeatRule"""")
            if (voiceEnabled) append(""", "voiceEnabled": true""")
            if (note != null) append(""", "note": "$note"""")
            append("}")
        }
    }

    /** 创建成功并返回 reminder id。 */
    fun createAndGetId(
        token: String,
        receiverId: Long,
        title: String = "吃降压药",
        kind: String = "ONCE",
        voiceEnabled: Boolean = false,
        fireAt: String = futureFireAt(),
        repeatRule: String? = null,
    ): Long {
        val body = create(
            token, receiverId, title,
            kind = kind, fireAt = fireAt, repeatRule = repeatRule, voiceEnabled = voiceEnabled,
        ).andExpect { status { isOk() } }.andReturn().response.contentAsString
        return Regex(""""id"\s*:\s*(\d+)""").find(body)!!.groupValues[1].toLong()
    }

    fun patch(token: String, id: Long, body: String) = mockMvc.patch("/api/v1/reminder/$id") {
        header("Authorization", "Bearer $token")
        contentType = MediaType.APPLICATION_JSON
        content = body
    }

    fun cancel(token: String, id: Long) = mockMvc.delete("/api/v1/reminder/$id") {
        header("Authorization", "Bearer $token")
    }

    fun mine(token: String): String = mockMvc.get("/api/v1/reminder/mine") {
        header("Authorization", "Bearer $token")
    }.andExpect { status { isOk() } }.andReturn().response.contentAsString

    fun incoming(token: String): String = mockMvc.get("/api/v1/reminder/incoming") {
        header("Authorization", "Bearer $token")
    }.andExpect { status { isOk() } }.andReturn().response.contentAsString

    fun mute(token: String, creatorId: Long, kind: String) = mockMvc.put("/api/v1/reminder/mute") {
        header("Authorization", "Bearer $token")
        contentType = MediaType.APPLICATION_JSON
        content = """{"creatorId": $creatorId, "kind": "$kind"}"""
    }

    fun unmute(token: String, creatorId: Long, kind: String) = mockMvc.delete("/api/v1/reminder/mute") {
        header("Authorization", "Bearer $token")
        contentType = MediaType.APPLICATION_JSON
        content = """{"creatorId": $creatorId, "kind": "$kind"}"""
    }

    fun mutedList(token: String): String = mockMvc.get("/api/v1/reminder/mute") {
        header("Authorization", "Bearer $token")
    }.andExpect { status { isOk() } }.andReturn().response.contentAsString

    fun parse(token: String, text: String) = mockMvc.post("/api/v1/reminder/parse") {
        header("Authorization", "Bearer $token")
        contentType = MediaType.APPLICATION_JSON
        content = """{"text": "$text"}"""
    }
}
