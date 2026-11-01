package com.bugu.shared.api

import jakarta.validation.ConstraintViolationException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import org.springframework.validation.BeanPropertyBindingResult
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException
import org.springframework.web.multipart.MaxUploadSizeExceededException
import java.lang.reflect.Method

/**
 * 全局异常处理:参数校验/约束违规 → 400、超限上传 → 413、未知异常 → 500,
 * 统一返回 ApiResponse 三段结构且不泄漏内部异常文案。
 */
class GlobalExceptionHandlerTest {

    private val handler = GlobalExceptionHandler()

    class Dummy {
        var name: String? = null

        @Suppress("UNUSED_PARAMETER")
        fun handle(name: String) = Unit
    }

    private fun methodParameter(): Method {
        val method = Dummy::class.java.getDeclaredMethod("handle", String::class.java)
        return method
    }

    @Test
    fun `参数校验异常_400且带字段详情`() {
        val parameter = org.springframework.core.MethodParameter(methodParameter(), 0)
        val binding = BeanPropertyBindingResult(Dummy(), "dummy")
        binding.rejectValue("name", "NotBlank", "不能为空")
        val response = handler.handleInvalidBody(MethodArgumentNotValidException(parameter, binding))

        assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)
        assertEquals("INVALID_ARGUMENT", response.body?.code)
        assertTrue(response.body?.message.orEmpty().contains("name"))
        assertTrue(response.body?.message.orEmpty().contains("不能为空"))
    }

    @Test
    fun `约束违规_400`() {
        val response = handler.handleViolation(ConstraintViolationException(emptySet()))
        assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)
        assertEquals("INVALID_ARGUMENT", response.body?.code)
    }

    @Test
    fun `超限上传_413`() {
        val response = handler.handleMaxUploadSize(MaxUploadSizeExceededException(3_000_000L))
        assertEquals(HttpStatus.PAYLOAD_TOO_LARGE, response.statusCode)
        assertEquals("INVALID_ARGUMENT", response.body?.code)
    }

    @Test
    fun `未知异常_500且不泄漏异常文案`() {
        val response = handler.handleUnexpected(RuntimeException("数据库密码泄露了"))
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.statusCode)
        assertEquals("INTERNAL", response.body?.code)
        assertFalse(response.body?.message.orEmpty().contains("数据库密码"))
    }

    @Test
    fun `参数类型不匹配_400`() {
        val response = handler.handleTypeMismatch(
            MethodArgumentTypeMismatchException(
                "abc",
                Long::class.java,
                "id",
                org.springframework.core.MethodParameter(methodParameter(), 0),
                null,
            ),
        )
        assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)
        assertEquals("INVALID_ARGUMENT", response.body?.code)
    }
}
