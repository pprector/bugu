package com.bugu.schedule

/**
 * 叫醒语文本缓存(E8-S4,纯逻辑):MUTUAL_ALARM 响铃时,优先播当日已缓存文案,
 * 无网或未命中回落 TTS 标题。key = reminderId:date(ISO yyyy-MM-dd)。
 */
class WakeupCopyCache(private val store: WakeupCopyStore) {

    interface WakeupCopyStore {
        fun get(key: String): String?
        fun put(key: String, content: String)
    }

    fun cache(reminderId: Long, date: String, content: String) {
        store.put("$reminderId:$date", content)
    }

    fun hit(reminderId: Long, date: String): String? = store.get("$reminderId:$date")
}
