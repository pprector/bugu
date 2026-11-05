package com.bugu.admin.app

import com.bugu.shared.api.BizException
import com.bugu.shared.api.ErrorCode
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertDoesNotThrow
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import java.time.Instant

/** 登录失败限流:5 次锁定 15 分钟,按 IP+账号隔离,时钟钩子驱动到期。 */
class AdminLoginThrottleTest {

    private val throttle = AdminLoginThrottle()
    private var current = Instant.parse("2026-11-05T08:00:00Z")

    init {
        throttle.now = { current }
    }

    private val key = "127.0.0.1|super"

    private fun failTimes(times: Int, target: String = key) {
        repeat(times) { throttle.onFailure(target) }
    }

    private fun assertLocked(target: String = key) {
        val error = assertThrows(BizException::class.java) { throttle.check(target) }
        assertEquals(ErrorCode.LOGIN_LOCKED, error.code)
        assertEquals(HttpStatus.TOO_MANY_REQUESTS, error.status)
        assertEquals(true, error.message?.contains("分钟后再试"))
    }

    @Test
    fun `连续失败5次_锁定`() {
        failTimes(AdminLoginThrottle.MAX_FAILURES - 1)
        assertDoesNotThrow { throttle.check(key) }

        throttle.onFailure(key)
        assertLocked()
    }

    @Test
    fun `未达阈值不锁定`() {
        failTimes(AdminLoginThrottle.MAX_FAILURES - 1)
        assertDoesNotThrow { throttle.check(key) }
    }

    @Test
    fun `按IP加账号隔离_其它key不受影响`() {
        failTimes(AdminLoginThrottle.MAX_FAILURES)
        assertLocked()
        assertDoesNotThrow { throttle.check("10.0.0.2|super") }
        assertDoesNotThrow { throttle.check("127.0.0.1|ops") }
    }

    @Test
    fun `锁定15分钟后自动解锁并重新计数`() {
        failTimes(AdminLoginThrottle.MAX_FAILURES)
        assertLocked()

        // 差 1 分钟仍在锁定期
        current = current.plus(AdminLoginThrottle.LOCK_DURATION).minusSeconds(60)
        assertLocked()

        // 到期后可继续尝试
        current = current.plusSeconds(60)
        assertDoesNotThrow { throttle.check(key) }
        // 解锁后重新计数:再失败 4 次不锁,第 5 次才锁
        failTimes(AdminLoginThrottle.MAX_FAILURES - 1)
        assertDoesNotThrow { throttle.check(key) }
        throttle.onFailure(key)
        assertLocked()
    }

    @Test
    fun `成功登录清零失败计数`() {
        failTimes(AdminLoginThrottle.MAX_FAILURES - 1)
        throttle.onSuccess(key)
        failTimes(AdminLoginThrottle.MAX_FAILURES - 1)
        assertDoesNotThrow { throttle.check(key) }
    }

    @Test
    fun `失败窗口超过15分钟重新计数`() {
        failTimes(AdminLoginThrottle.MAX_FAILURES - 1)

        // 窗口滚动到 15 分钟之后,旧计数作废
        current = current.plus(AdminLoginThrottle.WINDOW).plusSeconds(1)
        failTimes(AdminLoginThrottle.MAX_FAILURES - 1)
        assertDoesNotThrow { throttle.check(key) }
    }
}
