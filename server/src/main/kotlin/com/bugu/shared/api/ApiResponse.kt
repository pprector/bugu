package com.bugu.shared.api

import kotlinx.serialization.Serializable

/**
 * 全站统一响应体。字段固定为 code / message / data 三段,便于客户端按 code 驱动降级。
 */
@Serializable
data class ApiResponse<T>(
    val code: String = ErrorCode.OK.name,
    val message: String = ErrorCode.OK.defaultMessage,
    val data: T? = null,
) {
    companion object {
        fun <T> ok(data: T): ApiResponse<T> = ApiResponse(data = data)

        fun fail(code: ErrorCode, message: String? = null): ApiResponse<String> =
            ApiResponse(code = code.name, message = message ?: code.defaultMessage)
    }
}
