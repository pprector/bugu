package com.bugu.push

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** 推送点击解析:服务端 FIRE_AT extras 可直接构造 RingActivity 入参。 */
class PushRingTargetTest {

    @Test
    fun fireAtExtrasParseToRingTarget() {
        val target = PushRingTarget.parse(
            """{"fireId":"42","reminderId":"7","title":"吃降压药","fireAt":"2026-11-04T17:00:00","idempotencyKey":"7:2026-11-04T17:00","kind":"ONCE","voice":"false","selfReminder":"true","deeplink":"bugu://ring?fireId=42&reminderId=7"}"""
        )!!
        assertEquals(42L, target.fireId)
        assertEquals(7L, target.reminderId)
        assertEquals("吃降压药", target.title)
        assertEquals("2026-11-04T17:00:00", target.fireAt)
        assertEquals("7:2026-11-04T17:00", target.idempotencyKey)
        assertEquals("ONCE", target.kind)
        assertFalse(target.voiceEnabled)
        assertTrue(target.selfReminder)
    }

    @Test
    fun deeplinkSuppliesFireIdButMissingIdempotencyFallsBack() {
        assertNull(PushRingTarget.parse(null, "bugu://ring?fireId=42&reminderId=7"))
        assertNull(PushRingTarget.parse("""{"stage":"T1"}"""))
    }

    @Test
    fun oldPushWithoutFireIdFallsBackToMain() {
        assertNull(PushRingTarget.parse("""{"fireId":"42","stage":"T1"}"""))
    }
}
