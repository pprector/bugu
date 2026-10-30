package com.bugu.bond.domain

import java.time.LocalDateTime

/**
 * 1v1 绑定聚合。状态机 PENDING_OUTBOUND → ACTIVE → UNBOUND(终态,不可逆),
 * 状态变更只走聚合方法,禁止外部直改 status。
 *
 * 不变式:
 * - PENDING_OUTBOUND:receiverId 为空,bindCode/codeExpiresAt 非空
 * - ACTIVE:receiverId 非空,confirmedAt 非空,bindCode 已清空(一次性)
 */
class Bond(
    val id: Long?,
    val requesterId: Long,
    receiverId: Long?,
    status: BondStatus,
    bindCode: String?,
    codeExpiresAt: LocalDateTime?,
    confirmedAt: LocalDateTime?,
    unboundAt: LocalDateTime?,
    relationTag: String?,
    val createdAt: LocalDateTime,
) {

    var receiverId: Long? = receiverId
        private set

    var status: BondStatus = status
        private set

    var bindCode: String? = bindCode
        private set

    var codeExpiresAt: LocalDateTime? = codeExpiresAt
        private set

    var confirmedAt: LocalDateTime? = confirmedAt
        private set

    var unboundAt: LocalDateTime? = unboundAt
        private set

    var relationTag: String? = relationTag
        private set

    /** 发起方续发绑定码:仅 PENDING_OUTBOUND 状态可续(过期或首次发起)。 */
    fun reissueBindCode(code: String, expiresAt: LocalDateTime) {
        check(status == BondStatus.PENDING_OUTBOUND) { "仅 PENDING_OUTBOUND 可续发绑定码: $status" }
        bindCode = code
        codeExpiresAt = expiresAt
    }

    /**
     * 确认方接受绑定:回填接收人,进入 ACTIVE,清空绑定码。
     * 调用方需预先完成用户可见错误(码无效/过期/自绑/上限)的转换;此处为不变式兜底。
     */
    fun confirm(confirmerId: Long, now: LocalDateTime) {
        check(status == BondStatus.PENDING_OUTBOUND) { "当前状态不可确认: $status" }
        check(confirmerId != requesterId) { "不能与自己绑定" }
        check(receiverId == null) { "接收人已存在" }
        val code = requireNotNull(bindCode) { "绑定码缺失" }
        check(!BindCodePolicy.isExpired(requireNotNull(codeExpiresAt) { "过期时刻缺失" }, now)) { "绑定码 $code 已过期" }
        receiverId = confirmerId
        status = BondStatus.ACTIVE
        confirmedAt = now
        bindCode = null
        codeExpiresAt = null
    }

    /**
     * 解绑:仅 ACTIVE 可解绑(UNBOUND 为终态不可逆,PENDING_OUTBOUND 无解绑语义),
     * 仅双方之一可发起。解绑即时生效,双方查询接口不再返回该关系。
     */
    fun unbind(actorId: Long, now: LocalDateTime) {
        check(status == BondStatus.ACTIVE) { "当前状态不可解绑: $status" }
        check(actorId == requesterId || actorId == receiverId) { "非绑定双方不可解绑" }
        status = BondStatus.UNBOUND
        unboundAt = now
    }

    /** 运营后台管理员解绑(E9):同解绑语义,但不受「双方之一」限制;落审计由调用方负责。 */
    fun adminDissolve(now: LocalDateTime) {
        check(status == BondStatus.ACTIVE) { "当前状态不可解绑: $status" }
        status = BondStatus.UNBOUND
        unboundAt = now
    }

    /** 子女端代填关系标签:仅双方之一可改,限 16 字。 */
    fun updateRelationTag(actorId: Long, tag: String) {
        check(actorId == requesterId || actorId == receiverId) { "非绑定双方不可修改" }
        check(tag.isNotBlank()) { "关系标签不能为空" }
        relationTag = tag.take(16)
    }
}
