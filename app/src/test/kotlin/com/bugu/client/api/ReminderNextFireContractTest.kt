package com.bugu.client.api

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** ack 新契约:nextFireId/nextFireAt 可解析;旧服务端缺字段时保持 null 以走回退。 */
class ReminderNextFireContractTest {

    private val newAck = """
        {"id":7,"creatorId":1,"receiverId":2,"title":"喝水","kind":"REPEAT","tone":"GENTLE",
         "fireAt":"2026-11-04T17:00:00","voiceEnabled":false,"status":"ACKED","silent":false,
         "createdAt":"2026-11-01T00:00:00","fireId":null,"nextFireId":99,
         "nextFireAt":"2026-11-05T17:00:00"}
    """.trimIndent()

    private val oldAck = """
        {"id":7,"creatorId":1,"receiverId":2,"title":"喝水","kind":"ONCE","tone":"GENTLE",
         "fireAt":"2026-11-04T17:00:00","voiceEnabled":false,"status":"ACKED","silent":false,
         "createdAt":"2026-11-01T00:00:00","fireId":null}
    """.trimIndent()

    @Test
    fun newAckCarriesNextFire() {
        val view = ApiJson.json.decodeFromString(ReminderView.serializer(), newAck)
        assertEquals(99L, view.nextFireId)
        assertEquals("2026-11-05T17:00:00", view.nextFireAt)
    }

    @Test
    fun oldAckLeavesNextFireNullForFallback() {
        val view = ApiJson.json.decodeFromString(ReminderView.serializer(), oldAck)
        assertNull(view.nextFireId)
        assertNull(view.nextFireAt)
    }
}
