package com.bugu.bond.api

import kotlinx.serialization.Serializable

@Serializable
data class InitiateResponse(
    val bondId: Long,
    val bindCode: String,
    val deeplink: String,
    /** ISO-8601,绑定码过期时刻 */
    val expiresAt: String,
)

@Serializable
data class ConfirmRequest(
    val code: String,
)

@Serializable
data class InvitationDto(
    val bindCode: String,
    val requesterId: Long,
    /** ISO-8601,绑定码过期时刻 */
    val expiresAt: String,
)

@Serializable
data class BondDto(
    val id: Long,
    val status: String,
    /** 当前用户在该 bond 中的角色 */
    val role: String,
    /** 对方用户 id;PENDING_OUTBOUND 期间为 null */
    val counterpartId: Long?,
    /** ISO-8601 */
    val createdAt: String,
    /** ISO-8601;未确认为 null */
    val confirmedAt: String?,
    val remark: String? = null,
    val counterpartNickname: String? = null,
    val counterpartAvatarUrl: String? = null,
    val senderNickname: String? = null,
    val receiverNickname: String? = null,
    val senderAvatarUrl: String? = null,
    val receiverAvatarUrl: String? = null,
)

@Serializable
data class UpdateBondRemarkRequest(val remark: String?)
