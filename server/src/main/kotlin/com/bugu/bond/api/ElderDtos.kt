package com.bugu.bond.api

import com.bugu.identity.api.UserDto
import kotlinx.serialization.Serializable

@Serializable
data class ElderRegisterRequest(
    /** 客户端生成的稳定设备标识(幂等键) */
    val deviceUuid: String,
    val deviceModel: String,
    val inviteCode: String,
)

@Serializable
data class ElderRegisterResponse(
    val token: String,
    val user: UserDto,
)

@Serializable
data class ElderProfileRequest(
    val elderUserId: Long,
    val nickname: String? = null,
    val avatarUrl: String? = null,
    val relationTag: String? = null,
)

@Serializable
data class ElderProfileDto(
    val user: UserDto,
    val relationTag: String?,
)

/** 子女端标记/取消对方长辈模式(FR-06)。 */
@Serializable
data class ElderModeRequest(
    val peerId: Long,
    val enabled: Boolean,
)
