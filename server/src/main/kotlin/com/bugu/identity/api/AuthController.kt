package com.bugu.identity.api

import com.bugu.identity.app.AuthService
import com.bugu.identity.domain.Phone
import com.bugu.shared.api.ApiResponse
import com.bugu.shared.infra.CosUploadTicket
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * 注册登录接口(epic Requirements R1):验证码合一注册登录,JWT 登录态。
 */
@RestController
@RequestMapping("/api/v1/auth")
class AuthController(private val authService: AuthService) {

    @PostMapping("/send-code")
    fun sendCode(@RequestBody request: SendCodeRequest): ApiResponse<Unit> {
        authService.sendCode(Phone.of(request.phone))
        return ApiResponse.ok(Unit)
    }

    @PostMapping("/login")
    fun login(@RequestBody request: LoginRequest): ApiResponse<LoginResponse> {
        val response = authService.login(Phone.of(request.phone), request.code)
        return ApiResponse.ok(response)
    }

    @GetMapping("/me")
    fun me(currentUser: CurrentUser): ApiResponse<UserDto> =
        ApiResponse.ok(authService.me(currentUser.id))

    @PatchMapping("/me")
    fun updateProfile(currentUser: CurrentUser, @RequestBody request: UpdateProfileRequest): ApiResponse<UserDto> =
        ApiResponse.ok(authService.updateProfile(currentUser.id, request.nickname, request.avatarUrl))

    /** 头像直传第一步:按图片类型下发 COS 签名凭证(formFields 原样透传,file 字段最后)。 */
    @PostMapping("/me/avatar/token")
    fun avatarUploadTicket(currentUser: CurrentUser, @RequestBody request: AvatarTokenRequest): ApiResponse<CosUploadTicket> =
        ApiResponse.ok(authService.issueAvatarUploadTicket(currentUser.id, request.contentType))

    /** 头像直传第二步:直传 COS 成功后确认落库,返回更新后的资料(含 avatarUrl)。 */
    @PostMapping("/me/avatar/confirm")
    fun confirmAvatarUpload(currentUser: CurrentUser, @RequestBody request: AvatarConfirmRequest): ApiResponse<UserDto> =
        ApiResponse.ok(authService.confirmAvatarUpload(currentUser.id, request.objectKey))

    @PostMapping("/bind-phone")
    fun bindPhone(currentUser: CurrentUser, @RequestBody request: BindPhoneRequest): ApiResponse<UserDto> =
        ApiResponse.ok(authService.bindPhone(currentUser.id, Phone.of(request.phone), request.code))
}
