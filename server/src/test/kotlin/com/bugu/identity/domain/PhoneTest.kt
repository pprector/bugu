package com.bugu.identity.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class PhoneTest {

    @Test
    fun `合法手机号通过并去除首尾空白`() {
        assertEquals("13812345678", Phone.of(" 13812345678 ").value)
    }

    @Test
    fun `非法手机号拒绝`() {
        for (raw in listOf("12345678901", "10812345678", "1381234567", "138123456789", "abc", "")) {
            assertFailsWith<IllegalArgumentException>("应拒绝: $raw") { Phone.of(raw) }
        }
    }
}
