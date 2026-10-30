package com.bugu.shared.api

import jakarta.validation.ConstraintViolationException
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException
import org.springframework.web.multipart.MaxUploadSizeExceededException
import org.springframework.web.servlet.resource.NoResourceFoundException

/**
 * 全局异常 -> 统一响应体。错误码语义见 [ErrorCode]。
 */
@RestControllerAdvice
class GlobalExceptionHandler {

    private val log = LoggerFactory.getLogger(javaClass)

    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun handleInvalidBody(e: MethodArgumentNotValidException): ResponseEntity<ApiResponse<String>> {
        val detail = e.bindingResult.fieldErrors
            .joinToString("; ") { "${it.field}: ${it.defaultMessage}" }
        return respond(HttpStatus.BAD_REQUEST, ErrorCode.INVALID_ARGUMENT, detail)
    }

    @ExceptionHandler(ConstraintViolationException::class)
    fun handleViolation(e: ConstraintViolationException): ResponseEntity<ApiResponse<String>> =
        respond(HttpStatus.BAD_REQUEST, ErrorCode.INVALID_ARGUMENT, e.message)

    @ExceptionHandler(IllegalArgumentException::class)
    fun handleIllegalArgument(e: IllegalArgumentException): ResponseEntity<ApiResponse<String>> =
        respond(HttpStatus.BAD_REQUEST, ErrorCode.INVALID_ARGUMENT, e.message)

    /**
     * 请求体无法反序列化(畸形 JSON / 字段类型不符 / 空体)属调用方错误,
     * 不能落到兜底 500——否则端侧把参数问题误判为服务端故障(黑盒联验发现)。
     */
    @ExceptionHandler(HttpMessageNotReadableException::class)
    fun handleUnreadableBody(e: HttpMessageNotReadableException): ResponseEntity<ApiResponse<String>> {
        log.debug("unreadable request body: {}", e.message)
        return respond(HttpStatus.BAD_REQUEST, ErrorCode.INVALID_ARGUMENT, "请求体格式非法")
    }

    /** 未匹配路径返回 404 而非 500(黑盒联验发现误写路径曾被告成 INTERNAL)。 */
    @ExceptionHandler(NoResourceFoundException::class)
    fun handleNoResource(e: NoResourceFoundException): ResponseEntity<ApiResponse<String>> =
        respond(HttpStatus.NOT_FOUND, ErrorCode.NOT_FOUND, "接口不存在: ${e.resourcePath}")

    /** 路径/查询参数类型不符(如 id 传了非数字)同属调用方错误。 */
    @ExceptionHandler(MethodArgumentTypeMismatchException::class)
    fun handleTypeMismatch(e: MethodArgumentTypeMismatchException): ResponseEntity<ApiResponse<String>> =
        respond(HttpStatus.BAD_REQUEST, ErrorCode.INVALID_ARGUMENT, "参数 ${e.name} 类型非法")

    /** 请求体超过 multipart 容器上限(如头像 > 2MB)返回 413,而非落兜底 500。 */
    @ExceptionHandler(MaxUploadSizeExceededException::class)
    fun handleMaxUploadSize(e: MaxUploadSizeExceededException): ResponseEntity<ApiResponse<String>> =
        respond(HttpStatus.PAYLOAD_TOO_LARGE, ErrorCode.INVALID_ARGUMENT, "上传内容超过大小限制")

    @ExceptionHandler(BizException::class)
    fun handleBiz(e: BizException): ResponseEntity<ApiResponse<String>> =
        ResponseEntity.status(e.status).body(ApiResponse.fail(e.code, e.message))

    @ExceptionHandler(Exception::class)
    fun handleUnexpected(e: Exception): ResponseEntity<ApiResponse<String>> {
        log.error("unhandled exception", e)
        return respond(HttpStatus.INTERNAL_SERVER_ERROR, ErrorCode.INTERNAL, null)
    }

    private fun respond(
        status: HttpStatus,
        code: ErrorCode,
        message: String?,
    ): ResponseEntity<ApiResponse<String>> =
        ResponseEntity.status(status).body(ApiResponse.fail(code, message))
}
