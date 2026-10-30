package com.bugu.schedule

import android.content.Context
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
/** SharedPreferences 实现的三类本地存储:已注册闹钟 / 上报重试队列 / 叫醒语缓存。 */
class PrefsStores(context: Context) {

    private val prefs = context.getSharedPreferences("bugu_schedule", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }

    val scheduledAlarms: ScheduledAlarmStore = object : ScheduledAlarmStore {
        override fun load(): List<Long> =
            prefs.getString(KEY_SCHEDULED, null)?.let {
                json.decodeFromString(ListSerializer(Long.serializer()), it)
            } ?: emptyList()

        override fun save(fireIds: List<Long>) {
            prefs.edit().putString(KEY_SCHEDULED, json.encodeToString(ListSerializer(Long.serializer()), fireIds)).apply()
        }
    }

    val reportQueue: ReportQueueStore = object : ReportQueueStore {
        override fun load(): List<PendingReport> =
            prefs.getString(KEY_QUEUE, null)?.let {
                json.decodeFromString(ListSerializer(PendingReport.serializer()), it)
            } ?: emptyList()

        override fun save(items: List<PendingReport>) {
            prefs.edit().putString(KEY_QUEUE, json.encodeToString(ListSerializer(PendingReport.serializer()), items)).apply()
        }
    }

    val wakeupCopy: WakeupCopyCache.WakeupCopyStore = object : WakeupCopyCache.WakeupCopyStore {
        override fun get(key: String): String? = prefs.getString("$KEY_COPY_PREFIX$key", null)
        override fun put(key: String, content: String) {
            prefs.edit().putString("$KEY_COPY_PREFIX$key", content).apply()
        }
    }

    /** 最近一次 incoming 列表(BootReceiver 重排的源数据)。 */
    val lastIncoming: LoadableStore<List<com.bugu.client.api.ReminderView>> = object : LoadableStore<List<com.bugu.client.api.ReminderView>> {
        override fun load(): List<com.bugu.client.api.ReminderView> =
            prefs.getString(KEY_INCOMING, null)?.let {
                json.decodeFromString(ListSerializer(com.bugu.client.api.ReminderView.serializer()), it)
            } ?: emptyList()

        override fun save(items: List<com.bugu.client.api.ReminderView>) {
            prefs.edit().putString(
                KEY_INCOMING,
                json.encodeToString(ListSerializer(com.bugu.client.api.ReminderView.serializer()), items),
            ).apply()
        }
    }

    private companion object {
        const val KEY_SCHEDULED = "scheduled_alarms"
        const val KEY_QUEUE = "report_queue"
        const val KEY_INCOMING = "last_incoming"
        const val KEY_COPY_PREFIX = "wakeup_copy:"
    }
}

/** 简单可载入存储。 */
interface LoadableStore<T> {
    fun load(): T
    fun save(items: T)
}
