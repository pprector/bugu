package com.bugu.shared.api

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ApiResponseTest {

    @Test
    fun `ok carries data and OK code`() {
        val res = ApiResponse.ok("pong")
        assertEquals(ErrorCode.OK.name, res.code)
        assertEquals("pong", res.data)
    }

    @Test
    fun `fail has no data and keeps error code`() {
        val res = ApiResponse.fail(ErrorCode.AI_UNAVAILABLE)
        assertEquals(ErrorCode.AI_UNAVAILABLE.name, res.code)
        assertEquals(ErrorCode.AI_UNAVAILABLE.defaultMessage, res.message)
        assertNull(res.data)
    }
}
