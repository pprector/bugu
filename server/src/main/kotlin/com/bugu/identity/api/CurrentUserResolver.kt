package com.bugu.identity.api

import com.bugu.identity.infra.JwtAuthFilter
import com.bugu.shared.api.BizException
import com.bugu.shared.api.ErrorCode
import org.springframework.core.MethodParameter
import org.springframework.stereotype.Component
import org.springframework.web.bind.support.WebDataBinderFactory
import org.springframework.web.context.request.NativeWebRequest
import org.springframework.web.method.support.HandlerMethodArgumentResolver
import org.springframework.web.method.support.ModelAndViewContainer

/**
 * 当前登录用户(JWT 校验后注入)。控制器参数声明 [CurrentUser] 即可拿到 userId。
 */
data class CurrentUser(val id: Long)

@Component
class CurrentUserResolver : HandlerMethodArgumentResolver {

    override fun supportsParameter(parameter: MethodParameter): Boolean =
        parameter.parameterType == CurrentUser::class.java

    override fun resolveArgument(
        parameter: MethodParameter,
        mavContainer: ModelAndViewContainer?,
        webRequest: NativeWebRequest,
        binderFactory: WebDataBinderFactory?,
    ): Any {
        val userId = webRequest.getAttribute(JwtAuthFilter.ATTR_USER_ID, NativeWebRequest.SCOPE_REQUEST) as? Long
            ?: throw BizException(ErrorCode.UNAUTHORIZED, org.springframework.http.HttpStatus.UNAUTHORIZED)
        return CurrentUser(userId)
    }
}
