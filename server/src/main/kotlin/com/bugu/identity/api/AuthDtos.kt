package com.bugu.identity.api

import kotlinx.serialization.Serializable

@Serializable
data class SendCodeRequest(
    val phone: String,
)

@Serializable
data class LoginRequest(
    val phone: String,
    val code: String,
)

@Serializable
data class BindPhoneRequest(
    val phone: String,
    val code: String,
)

/** 编辑个人资料:字段可缺省,缺省表示不更新;至少传一项。 */
@Serializable
data class UpdateProfileRequest(
    val nickname: String? = null,
    val avatarUrl: String? = null,
)

/** 头像直传凭证申请:只传图片 MIME 类型,大小与对象命名由服务端决定。 */
@Serializable
data class AvatarTokenRequest(
    val contentType: String,
)

/** 头像直传完成确认:回传凭证里的 objectKey。 */
@Serializable
data class AvatarConfirmRequest(
    val objectKey: String,
)

@Serializable
data class UserDto(
    val id: Long,
    val nickname: String,
    val phone: String?,
    val accountType: String,
    val elderMode: Boolean = false,
    val avatarUrl: String? = null,
)

@Serializable
data class LoginResponse(
    val token: String,
    val user: UserDto,
)
