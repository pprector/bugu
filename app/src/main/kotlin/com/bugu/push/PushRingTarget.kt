package com.bugu.push

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * 推送点击 → 响铃页 的入参(E8-S7 服务端 FIRE_AT 契约):
 * 服务端 extras 同时下发 fireId/reminderId/title/fireAt/idempotencyKey/kind/voice/selfReminder,
 * 端侧直接构造 RingActivity Intent,不再经 List 查询。缺 fireId 或幂等键时返回 null,由调用方回退主界面。
 */
data class PushRingTarget(
    val fireId: Long,
    val reminderId: Long,
    val title: String,
    val fireAt: String,
    val idempotencyKey: String,
    val voiceEnabled: Boolean,
    val kind: String,
    val selfReminder: Boolean,
    val deeplink: String? = null,
) {
    companion object {
        private val json = Json { ignoreUnknownKeys = true }
        private val deeplinkFireId = Regex("""[?&]fireId=([^&]+)""")

        /** 解析 JPush NotificationMessage.notificationExtras;deeplink 仅作 fireId 缺失时的兜底。 */
        fun parse(extrasJson: String?, deeplink: String? = null): PushRingTarget? {
            val obj = extrasJson?.let {
                runCatching { json.parseToJsonElement(it).jsonObject }.getOrNull()
            } ?: JsonObject(emptyMap())
            val fireId = obj.string("fireId")?.toLongOrNull()
                ?: deeplink?.let { link -> deeplinkFireId.find(link)?.groupValues?.getOrNull(1)?.toLongOrNull() }
                ?: return null
            val idempotencyKey = obj.string("idempotencyKey").orEmpty()
            // 无幂等键无法上报触发,继续进响铃页会制造无效补报;回退主界面。
            if (idempotencyKey.isBlank()) return null
            return PushRingTarget(
                fireId = fireId,
                reminderId = obj.string("reminderId")?.toLongOrNull() ?: -1,
                title = obj.string("title")?.takeIf { it.isNotBlank() } ?: "到点了",
                fireAt = obj.string("fireAt").orEmpty(),
                idempotencyKey = idempotencyKey,
                voiceEnabled = obj.string("voice").toBoolean(),
                kind = obj.string("kind")?.takeIf { it.isNotBlank() } ?: "ONCE",
                selfReminder = obj.string("selfReminder").toBoolean(),
                deeplink = deeplink ?: obj.string("deeplink"),
            )
        }

        private fun JsonObject.string(key: String): String? =
            this[key]?.jsonPrimitive?.contentOrNull

        private fun String?.toBoolean(): Boolean =
            this == "true" || this == "1"
    }
}
