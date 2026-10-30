package com.bugu.shared.api

import org.springframework.http.HttpStatus

/**
 * 业务异常:携带 [ErrorCode] 与 HTTP 状态,统一交给 GlobalExceptionHandler 转统一响应体,
 * 业务代码不自行 try-catch 拼响应。
 */
class BizException(
    val code: ErrorCode,
    val status: HttpStatus = HttpStatus.BAD_REQUEST,
    message: String? = null,
) : RuntimeException(message ?: code.defaultMessage)
