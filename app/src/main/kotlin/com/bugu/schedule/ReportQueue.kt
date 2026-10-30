package com.bugu.schedule

import kotlinx.serialization.Serializable

/**
 * 火灾上报重试队列(E8-S4,纯逻辑):响铃时上报触发事实,失败入队,WorkManager 退避重试。
 * 409(已收口/已错过)→ 移出队列并标记本地已错过,不重试;5xx/网络错误 → 计数退避。
 */
@Serializable
data class PendingReport(
    val fireId: Long,
    val idempotencyKey: String,
    val source: String,
    val attempts: Int = 0,
)

interface ReportQueueStore {
    fun load(): List<PendingReport>
    fun save(items: List<PendingReport>)
}

class ReportQueue(private val store: ReportQueueStore) {

    /** 第 attempts 次重试退避 30s×attempts,封顶 15min。 */
    fun backoffSeconds(attempts: Int): Long =
        (30L * attempts.coerceAtLeast(1)).coerceAtMost(900L)

    fun enqueue(item: PendingReport) {
        val current = store.load().filterNot { it.fireId == item.fireId }
        store.save(current + item)
    }

    fun all(): List<PendingReport> = store.load()

    /** 成功:移除。 */
    fun markDone(fireId: Long) {
        store.save(store.load().filterNot { it.fireId == fireId })
    }

    /** 409 已错过:移除(调用方负责本地标记「已错过」展示)。 */
    fun markMissed(fireId: Long): Boolean {
        val existed = store.load().any { it.fireId == fireId }
        markDone(fireId)
        return existed
    }

    /** 重试失败:attempts+1 并保留;返回下次退避秒数。 */
    fun markRetry(fireId: Long): Long {
        val updated = store.load().map {
            if (it.fireId == fireId) it.copy(attempts = it.attempts + 1) else it
        }
        store.save(updated)
        return backoffSeconds(updated.first { it.fireId == fireId }.attempts)
    }
}
