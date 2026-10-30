package com.bugu.bond.app

import com.bugu.bond.api.ElderProfileDto
import com.bugu.bond.api.ElderProfileRequest
import com.bugu.bond.api.ElderRegisterResponse
import com.bugu.bond.domain.BondStatus
import com.bugu.bond.domain.BindCodePolicy
import com.bugu.bond.infra.BondMapper
import com.bugu.bond.infra.BondRepository
import com.bugu.bond.infra.hasActiveBond
import com.bugu.identity.api.UserDto
import com.bugu.identity.app.UserService
import com.bugu.identity.infra.JwtTokenService
import com.bugu.shared.api.BizException
import com.bugu.shared.api.ErrorCode
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.support.TransactionTemplate
import java.time.Clock
import java.time.LocalDateTime

/**
 * 长辈免注册接入:设备标识注册(幂等)+ 与邀请码发起方直建 ACTIVE bond + 子女端代填资料。
 * 依赖方向 bond → identity 单向。
 */
@Service
class ElderBondService(
    private val bondRepository: BondRepository,
    private val userService: UserService,
    private val jwtTokenService: JwtTokenService,
    private val clock: Clock,
    private val transactionTemplate: TransactionTemplate,
) {

    private val log = LoggerFactory.getLogger(javaClass)

    /**
     * 设备上报注册:同 deviceUuid 重复上报幂等直登(不重复建号);
     * 新设备凭有效邀请码建 elder 号并与发起方直建 ACTIVE bond(跳过确认步骤,邀请码即同意)。
     */
    fun register(deviceUuid: String, deviceModel: String, inviteCode: String): ElderRegisterResponse {
        val now = LocalDateTime.now(clock)
        return transactionTemplate.execute {
            userService.findByDeviceUuid(deviceUuid)?.let { existing ->
                log.info("长辈设备重复注册,幂等登录 userId={}", existing.id)
                return@execute ElderRegisterResponse(
                    token = jwtTokenService.issue(existing.id!!, null),
                    user = userService.toDto(existing),
                )
            }

            val row = bondRepository.findFirstByBindCodeAndStatus(inviteCode, BondStatus.PENDING_OUTBOUND.name)
                ?: throw BizException(ErrorCode.BOND_INVALID, HttpStatus.BAD_REQUEST, "邀请码无效")
            val expiresAt = row.codeExpiresAt
            if (expiresAt != null && BindCodePolicy.isExpired(expiresAt, now)) {
                throw BizException(ErrorCode.BOND_INVALID, HttpStatus.BAD_REQUEST, "邀请码已过期")
            }
            requireNoActiveBond(row.requesterId)

            val elder = userService.createElder(deviceUuid, deviceModel)
            val domain = BondMapper.toDomain(row)
            domain.confirm(elder.id!!, now)
            val saved = bondRepository.save(BondMapper.toRow(domain))
            log.info("长辈账号注册并绑定 elderUserId={} bondId={} requester={}", elder.id, saved.id, row.requesterId)
            ElderRegisterResponse(
                token = jwtTokenService.issue(elder.id, null),
                user = userService.toDto(elder),
            )
        }
    }

    /** 子女端代填长辈资料:昵称/头像落到 app_user,关系标签落到 bond,需双方存在 ACTIVE 绑定。 */
    fun updateElderProfile(childId: Long, request: ElderProfileRequest): ElderProfileDto {
        val elderId = request.elderUserId
        if (elderId == childId) {
            throw BizException(ErrorCode.BOND_INVALID, HttpStatus.BAD_REQUEST, "不能操作自己")
        }
        val bondRow = findActiveBondBetween(childId, elderId)
            ?: throw BizException(ErrorCode.FORBIDDEN, HttpStatus.FORBIDDEN, "与该长辈无绑定关系")

        val updatedUser = userService.updateProfile(elderId, request.nickname, request.avatarUrl)
        val relationTag = request.relationTag?.let { tag ->
            val domain = BondMapper.toDomain(bondRow)
            domain.updateRelationTag(childId, tag)
            bondRepository.save(BondMapper.toRow(domain)).relationTag
        }
        return ElderProfileDto(user = userService.toDto(updatedUser), relationTag = relationTag ?: bondRow.relationTag)
    }

    /**
     * 子女端标记/取消 bond 对方为长辈模式(FR-06):即时生效。
     * 仅 ACTIVE bond 双方可互相标记(通常子女标记长辈);不能标记自己。
     */
    fun setElderMode(actorId: Long, peerId: Long, enabled: Boolean): UserDto {
        if (peerId == actorId) {
            throw BizException(ErrorCode.BOND_INVALID, HttpStatus.BAD_REQUEST, "不能操作自己")
        }
        findActiveBondBetween(actorId, peerId)
            ?: throw BizException(ErrorCode.FORBIDDEN, HttpStatus.FORBIDDEN, "与对方无绑定关系")
        val updated = userService.setElderMode(peerId, enabled)
        log.info("elder mode set userId={} enabled={} by={}", peerId, enabled, actorId)
        return userService.toDto(updated)
    }

    private fun findActiveBondBetween(a: Long, b: Long) =
        bondRepository.findFirstByRequesterIdAndReceiverIdAndStatus(a, b, BondStatus.ACTIVE.name)
            ?: bondRepository.findFirstByReceiverIdAndRequesterIdAndStatus(a, b, BondStatus.ACTIVE.name)

    private fun requireNoActiveBond(userId: Long) {
        if (bondRepository.hasActiveBond(userId)) {
            throw BizException(ErrorCode.BOND_LIMITED, HttpStatus.CONFLICT, "已有绑定关系,上限 1 条")
        }
    }
}
