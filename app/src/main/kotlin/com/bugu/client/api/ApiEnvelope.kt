package com.bugu.client.api

import kotlinx.serialization.Serializable

/**
 * 服务端统一响应信封(与 server shared/api/ApiResponse 同形)。
 * server SerializationConfig:encodeDefaults=true、explicitNulls=false——
 * null 字段在 wire 上省略,因此 message/data 必须带默认值才能解码缺失字段。
 */
@Serializable
data class ApiEnvelope<T>(
    val code: String,
    val message: String? = null,
    val data: T? = null,
) {
    val isOk: Boolean get() = code == "OK"
}

/** 业务错误:信封 code != OK 时抛出,message 取服务端文案,缺省回落错误码。 */
class GeitaApiException(
    val code: String,
    message: String?,
) : RuntimeException(message ?: code)

/** 客户端解码配置:容忍服务端后续新增字段;null 缺省按默认值补齐。 */
object ApiJson {
    val json: kotlinx.serialization.json.Json = kotlinx.serialization.json.Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        encodeDefaults = true
    }
}
