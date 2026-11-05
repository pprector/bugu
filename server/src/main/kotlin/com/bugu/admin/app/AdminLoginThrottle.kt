package com.bugu.admin.app

import com.bugu.shared.api.BizException
import com.bugu.shared.api.ErrorCode
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import java.time.Duration
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.ceil

/**
 * 后台登录失败限流:同一 IP+账号在 15 分钟窗口内失败 5 次即锁定 15 分钟。
 * 计数保存在单实例内存(生产为单容器部署);重启会清空计数,属已知限制,已写入验收报告。
 */
@Component
class AdminLoginThrottle {

    private data class Attempts(
        var failures: Int = 0,
        var windowStart: Instant? = null,
        var lockedUntil: Instant? = null,
    )

    private val attempts = ConcurrentHashMap<String, Attempts>()

    /** 时钟钩子:测试可替换,生产默认取系统时间。 */
    internal var now: () -> Instant = Instant::now

    /** 登录前检查:仍处于锁定期则直接拒绝。 */
    fun check(key: String) {
        val state = attempts[key] ?: return
        val until = state.lockedUntil ?: return
        val current = now()
        if (current.isBefore(until)) {
            val remainMinutes = ceil(Duration.between(current, until).toMillis() / 60_000.0).toLong().coerceAtLeast(1)
            throw BizException(
                ErrorCode.LOGIN_LOCKED,
                HttpStatus.TOO_MANY_REQUESTS,
                "登录失败次数过多，请 $remainMinutes 分钟后再试",
            )
        }
        attempts.remove(key, state)
    }

    /** 登录失败记账:达到阈值即锁定。 */
    fun onFailure(key: String) {
        val current = now()
        prune(current)
        val state = attempts.computeIfAbsent(key) { Attempts() }
        synchronized(state) {
            if (state.lockedUntil != null && current.isBefore(state.lockedUntil)) return
            val windowStart = state.windowStart
            if (windowStart == null || Duration.between(windowStart, current) > WINDOW) {
                state.windowStart = current
                state.failures = 0
                state.lockedUntil = null
            }
            state.failures += 1
            if (state.failures >= MAX_FAILURES) {
                state.lockedUntil = current.plus(LOCK_DURATION)
            }
        }
    }

    /** 登录成功:清零该 IP+账号的失败计数。 */
    fun onSuccess(key: String) {
        attempts.remove(key)
    }

    private fun prune(current: Instant) {
        attempts.entries.removeIf { (_, state) ->
            val lockedUntil = state.lockedUntil
            if (lockedUntil != null) {
                current.isAfter(lockedUntil)
            } else {
                state.windowStart?.let { current.isAfter(it.plus(WINDOW)) } ?: true
            }
        }
    }

    companion object {
        const val MAX_FAILURES = 5
        val WINDOW: Duration = Duration.ofMinutes(15)
        val LOCK_DURATION: Duration = Duration.ofMinutes(15)
    }
}
