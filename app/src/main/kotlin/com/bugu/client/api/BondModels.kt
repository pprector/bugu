package com.bugu.client.api

import kotlinx.serialization.Serializable

/** 绑定码发起响应:server bond/api/InitiateResponse 同形。POST /api/v1/bond/initiate */
@Serializable
data class InitiateResponse(
    val bondId: Long,
    val bindCode: String,
    val deeplink: String,
    /** ISO-8601,绑定码过期时刻 */
    val expiresAt: String,
)

/** 绑定码确认。POST /api/v1/bond/confirm */
@Serializable
data class ConfirmRequest(
    val code: String,
)

/** 扫码/深链进入时按码查邀请方。GET /api/v1/bond/by-code/{code} */
@Serializable
data class InvitationDto(
    val bindCode: String,
    val requesterId: Long,
    /** ISO-8601,绑定码过期时刻 */
    val expiresAt: String,
)

/** 绑定关系视图:新增资料字段可缺省，备注仅当前调用者可见。 */
@Serializable
data class BondDto(
    val id: Long,
    val status: String,
    /** 当前用户在该 bond 中的角色 */
    val role: String,
    /** 对方用户 id;PENDING_OUTBOUND 期间为 null */
    val counterpartId: Long? = null,
    /** ISO-8601 */
    val createdAt: String,
    /** ISO-8601;未确认为 null */
    val confirmedAt: String? = null,
    /** 对方昵称(如「妈妈」「阿哲」),待服务端补充 */
    val counterpartNickname: String? = null,
    /** 对方关系标签(如「妈妈」),待服务端补充 */
    val counterpartRelationTag: String? = null,
    /** 对方是否处于长辈模式,待服务端补充 */
    val counterpartElderMode: Boolean? = null,
    val remark: String? = null,
    val senderNickname: String? = null,
    val receiverNickname: String? = null,
    val senderAvatarUrl: String? = null,
    val receiverAvatarUrl: String? = null,
    val counterpartAvatarUrl: String? = null,
)

val BondDto.displayName: String
    get() = remark?.trim()?.takeIf { it.isNotEmpty() }
        ?: counterpartNickname?.trim()?.takeIf { it.isNotEmpty() }
        ?: (if (role == "REQUESTER") receiverNickname else senderNickname)?.trim()?.takeIf { it.isNotEmpty() }
        ?: "亲友"

@Serializable
data class BondRemarkRequest(val remark: String?) {
    init { require(remark == null || remark.length <= 30) }

    fun toJson() = kotlinx.serialization.json.buildJsonObject {
        put("remark", remark?.let { kotlinx.serialization.json.JsonPrimitive(it) } ?: kotlinx.serialization.json.JsonNull)
    }
}

/** 子女端标记/取消对方长辈模式(FR-06)。POST /api/v1/bond/elder-mode */
@Serializable
data class ElderModeRequest(
    val peerId: Long,
    val enabled: Boolean,
)

/** 长辈资料维护。POST /api/v1/bond/elder-profile */
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
    val relationTag: String? = null,
)
