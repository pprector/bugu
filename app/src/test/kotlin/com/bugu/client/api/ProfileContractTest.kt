package com.bugu.client.api

import org.junit.Test
import kotlin.test.*

class ProfileContractTest {
    @Test fun feedbackTrimsBeforeValidatingBoundaries() {
        assertEquals("十".repeat(10), CreateFeedbackRequest.validated("REMINDER", "  ${"十".repeat(10)}  ", "1.0").content)
        assertEquals(2000, CreateFeedbackRequest.validated("OTHER", "字".repeat(2000), "1.0").content.length)
        listOf(" ", "字".repeat(9), "字".repeat(2001)).forEach {
            assertFailsWith<IllegalArgumentException> { CreateFeedbackRequest.validated("OTHER", it, "1.0") }
        }
        assertFailsWith<IllegalArgumentException> { CreateFeedbackRequest.validated("UNKNOWN", "字".repeat(10), "1.0") }
    }

    @Test fun feedbackDecodesServerReplyAndNullableFields() {
        val feedback = ApiJson.json.decodeFromString<FeedbackDto>("""{"id":7,"category":"ACCOUNT","content":"反馈内容至少十个字符","appVersion":null,"status":"RESOLVED","reply":"已处理","createdAt":"2026-10-30T10:00:00","updatedAt":"2026-10-30T11:00:00"}""")
        assertEquals("已处理", feedback.reply)
        assertNull(feedback.appVersion)
        assertEquals("已解决", feedbackStatusLabel(feedback.status))
        assertEquals("未知状态（NEW）", feedbackStatusLabel("NEW"))
    }

    @Test fun nicknamePayloadDoesNotClearAvatar() {
        val payload = ApiJson.json.encodeToString(UpdateProfileRequest.serializer(), UpdateProfileRequest("新昵称"))
        assertEquals("{\"nickname\":\"新昵称\"}", payload)
    }
}
