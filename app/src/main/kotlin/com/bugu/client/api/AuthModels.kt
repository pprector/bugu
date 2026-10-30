package com.bugu.client.api

import kotlinx.serialization.Serializable

/** 发送短信验证码。POST /api/v1/auth/send-code */
@Serializable
data class SendCodeRequest(
    val phone: String,
)

/** 手机号 + 验证码登录。POST /api/v1/auth/login */
@Serializable
data class LoginRequest(
    val phone: String,
    val code: String,
)

/** 长辈设备标识账号补绑手机号。POST /api/v1/auth/bind-phone */
@Serializable
data class BindPhoneRequest(
    val phone: String,
    val code: String,
)

/** 用户视图:server identity/api/UserDto 同形。 */
@Serializable
data class UserDto(
    val id: Long,
    val nickname: String,
    val phone: String? = null,
    val accountType: String,
    val elderMode: Boolean = false,
    val avatarUrl: String? = null,
)

/** 登录响应:JWT 与用户视图。 */
@Serializable
data class LoginResponse(
    val token: String,
    val user: UserDto,
)

/** 头像直传凭证申请。POST /api/v1/auth/me/avatar/token */
@Serializable
data class AvatarTokenRequest(
    val contentType: String,
)

/** 头像直传完成确认。POST /api/v1/auth/me/avatar/confirm */
@Serializable
data class AvatarConfirmRequest(
    val objectKey: String,
)

/** COS PostObject 签名直传凭证:server shared/infra/CosUploadTicket 同形。 */
@Serializable
data class AvatarUploadTicket(
    val uploadUrl: String,
    val objectKey: String,
    val publicUrl: String,
    val formFields: Map<String, String>,
    val maxBytes: Long,
    val expiresInSeconds: Long,
)

/** 长辈免注册接入(E1):设备标识 + 邀请码换 token。POST /api/v1/auth/elder-register */
@Serializable
data class ElderRegisterRequest(
    val deviceUuid: String,
    val deviceModel: String,
    val inviteCode: String,
)

@Serializable
data class ElderRegisterResponse(
    val token: String,
    val user: UserDto,
)

/** 推送令牌上报(E8-S5):极光 registrationID 或后续厂商 token。POST /api/v1/devices/token */
@Serializable
data class DeviceTokenReport(
    val pushToken: String,
    val platform: String,
    val pushEnabled: Boolean = true,
    val vendor: String = "JIGUANG",
)

/** 上报回执:server identity/api/DeviceTokenView 同形。 */
@Serializable
data class DeviceTokenView(
    val id: Long,
    val platform: String,
    val vendor: String,
    val pushEnabled: Boolean,
)
