package com.bugu.bond.app

import com.bugu.bond.api.BondDto
import com.bugu.bond.api.InitiateResponse
import com.bugu.bond.api.InvitationDto
import com.bugu.bond.domain.Bond
import com.bugu.bond.domain.BondStatus
import com.bugu.bond.domain.BindCodePolicy
import com.bugu.bond.domain.ConfirmThrottlePolicy
import com.bugu.bond.infra.BondMapper
import com.bugu.bond.infra.BondRepository
import com.bugu.bond.infra.BondRow
import com.bugu.bond.infra.ConfirmThrottleRepository
import com.bugu.identity.infra.UserRepository
import com.bugu.shared.api.BizException
import com.bugu.shared.api.ErrorCode
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import org.springframework.transaction.support.TransactionTemplate
import java.security.SecureRandom
import java.time.Clock
import java.time.LocalDateTime

/**
 * 1v1 绑定应用服务:发起绑定码(幂等续发)、确认接受、双方查询。
 * 状态变更经 [Bond] 聚合方法;用户可见错误在此转为 BizException,聚合内为不变式兜底。
 * confirm 带失败限流(防 6 位码暴力枚举),失败计数落 DB,成功清零。
 */
@Service
class BondService(
    private val bondRepository: BondRepository,
    private val userRepository: UserRepository,
    private val bondLifecycle: BondLifecycle,
    private val confirmThrottleRepository: ConfirmThrottleRepository,
    private val clock: Clock,
    private val transactionTemplate: TransactionTemplate,
) {

    private val log = LoggerFactory.getLogger(javaClass)
    private val random = SecureRandom()

    /**
     * 发起绑定:同一发起人仅一条 PENDING_OUTBOUND——码未过期原样返回(幂等),
     * 过期则同一行续发新码,均不重复创建(合并语义)。
     */
    fun initiate(requesterId: Long): InitiateResponse {
        val now = LocalDateTime.now(clock)
        return transactionTemplate.execute {
            userRepository.lockUser(requesterId)
            val existing = bondRepository.findFirstByRequesterIdAndStatus(requesterId, BondStatus.PENDING_OUTBOUND.name)

            val bond: Bond = if (existing != null &&
                existing.codeExpiresAt != null &&
                !BindCodePolicy.isExpired(existing.codeExpiresAt, now)
            ) {
                BondMapper.toDomain(existing)
            } else {
                val code = generateCode()
                val expiresAt = now.plus(BindCodePolicy.CODE_TTL)
                if (existing != null) {
                    val domain = BondMapper.toDomain(existing)
                    domain.reissueBindCode(code, expiresAt)
                    BondMapper.toDomain(bondRepository.save(BondMapper.toRow(domain)))
                } else {
                    BondMapper.toDomain(
                        bondRepository.save(
                            BondRow(
                                requesterId = requesterId,
                                bindCode = code,
                                codeExpiresAt = expiresAt,
                            ),
                        ),
                    )
                }
            }
            val code = requireNotNull(bond.bindCode) { "发起后绑定码缺失" }
            InitiateResponse(
                bondId = bond.id!!,
                bindCode = code,
                deeplink = deeplink(code),
                expiresAt = requireNotNull(bond.codeExpiresAt).toString(),
            )
        }
    }

    /**
     * 确认方接受绑定:回填接收人,bond 进入 ACTIVE。
     * 失败限流:锁定中直接 429;码无效/过期/自绑/上限均计一次失败,成功清零。
     */
    fun confirm(confirmerId: Long, code: String): BondDto {
        val now = LocalDateTime.now(clock)
        val throttle = confirmThrottleRepository.find(confirmerId)
        when (val decision = ConfirmThrottlePolicy.evaluateBeforeConfirm(throttle?.lockedUntil, now)) {
            is ConfirmThrottlePolicy.Decision.Denied -> {
                log.info("confirm 限流拒绝 confirmer={} lockedUntil={}", confirmerId, decision.lockedUntil)
                throw BizException(ErrorCode.BOND_INVALID, HttpStatus.TOO_MANY_REQUESTS, "尝试过于频繁,请稍后再试")
            }

            ConfirmThrottlePolicy.Decision.Allowed -> Unit
        }
        return try {
            transactionTemplate.execute {
                val invitation = bondRepository.findFirstByBindCodeAndStatus(code, BondStatus.PENDING_OUTBOUND.name)
                    ?: throw BizException(ErrorCode.BOND_INVALID, HttpStatus.BAD_REQUEST, "绑定码无效")
                listOf(invitation.requesterId, confirmerId).distinct().sorted().forEach { userRepository.lockUser(it) }
                val row = bondRepository.findFirstByBindCodeAndStatus(code, BondStatus.PENDING_OUTBOUND.name)
                    ?: throw BizException(ErrorCode.BOND_INVALID, HttpStatus.BAD_REQUEST, "绑定码无效")
                val expiresAt = row.codeExpiresAt
                if (expiresAt != null && BindCodePolicy.isExpired(expiresAt, now)) {
                    throw BizException(ErrorCode.BOND_INVALID, HttpStatus.BAD_REQUEST, "绑定码已过期")
                }
                if (row.requesterId == confirmerId) {
                    throw BizException(ErrorCode.BOND_INVALID, HttpStatus.BAD_REQUEST, "不能与自己绑定")
                }
                if (bondRepository.findFirstByRequesterIdAndReceiverIdAndStatus(row.requesterId, confirmerId, "ACTIVE") != null ||
                    bondRepository.findFirstByRequesterIdAndReceiverIdAndStatus(confirmerId, row.requesterId, "ACTIVE") != null
                ) throw BizException(ErrorCode.BOND_INVALID, HttpStatus.CONFLICT, "双方已有有效绑定关系")

                val domain = BondMapper.toDomain(row)
                domain.confirm(confirmerId, now)
                val saved = bondRepository.save(BondMapper.toRow(domain))
                log.info("bond 确认成立 id={} requester={} receiver={}", saved.id, saved.requesterId, saved.receiverId)
                saved.toDto(confirmerId)
            }.also {
                confirmThrottleRepository.delete(confirmerId)
            }
        } catch (e: BizException) {
            recordConfirmFailure(confirmerId, now)
            throw e
        }
    }

    /** 扫码方拉取待确认邀请预览。 */
    fun invitation(code: String): InvitationDto {
        val row = bondRepository.findFirstByBindCodeAndStatus(code, BondStatus.PENDING_OUTBOUND.name)
            ?: throw BizException(ErrorCode.BOND_INVALID, HttpStatus.BAD_REQUEST, "邀请不存在或已被处理")
        return InvitationDto(
            bindCode = code,
            requesterId = row.requesterId,
            expiresAt = (row.codeExpiresAt ?: LocalDateTime.now(clock)).toString(),
        )
    }

    /**
     * 解绑:仅 ACTIVE、仅双方之一。解绑即时生效,双方查询接口不再返回该关系;
     * 再次扫码视为新关系。UNBOUND 为终态,重复解绑返回 BOND_INVALID。
     */
    fun unbind(userId: Long, bondId: Long): BondDto {
        val now = LocalDateTime.now(clock)
        return transactionTemplate.execute {
            val row = bondRepository.lockById(bondId)
                ?: throw BizException(ErrorCode.NOT_FOUND, HttpStatus.NOT_FOUND, "绑定关系不存在")
            if (userId != row.requesterId && userId != row.receiverId) {
                throw BizException(ErrorCode.FORBIDDEN, HttpStatus.FORBIDDEN)
            }
            if (row.status != BondStatus.ACTIVE.name) {
                throw BizException(ErrorCode.BOND_INVALID, HttpStatus.CONFLICT, "当前状态不可解绑")
            }
            val domain = BondMapper.toDomain(row)
            domain.unbind(userId, now)
            val saved = bondRepository.save(BondMapper.toRow(domain))
            bondLifecycle.deactivate(saved.id!!)
            saved.toDto(userId)
        }
    }

    /** 我的绑定列表:ACTIVE 双方可见,PENDING_OUTBOUND 仅发起方可见,UNBOUND 双方不可见(解绑即时隔离)。 */
    fun listMine(userId: Long): List<BondDto> =
        bondRepository.findByRequesterIdOrReceiverId(userId, userId)
            .filter {
                when (BondStatus.valueOf(it.status)) {
                    BondStatus.ACTIVE -> true
                    BondStatus.PENDING_OUTBOUND -> it.requesterId == userId
                    BondStatus.UNBOUND -> false
                }
            }
            .map { it.toDto(userId) }

    fun updateRemark(userId: Long, bondId: Long, remark: String?): BondDto {
        if (remark != null && remark.length > 30) {
            throw BizException(ErrorCode.INVALID_ARGUMENT, HttpStatus.BAD_REQUEST, "备注不超过30字")
        }
        return transactionTemplate.execute {
            val row = bondRepository.lockById(bondId)
                ?: throw BizException(ErrorCode.NOT_FOUND, HttpStatus.NOT_FOUND, "绑定关系不存在")
            if (userId != row.requesterId && userId != row.receiverId) {
                throw BizException(ErrorCode.FORBIDDEN, HttpStatus.FORBIDDEN)
            }
            if (row.status != "ACTIVE") throw BizException(ErrorCode.BOND_INVALID, HttpStatus.CONFLICT)
            val domain = BondMapper.toDomain(row)
            domain.updateRemark(userId, remark?.trim()?.takeIf { it.isNotEmpty() })
            bondRepository.save(BondMapper.toRow(domain)).toDto(userId)
        }
    }

    /** 确认失败计数:窗口内连续失败达上限即写入锁(与短信限流同语义,DB 计数)。 */
    private fun recordConfirmFailure(confirmerId: Long, now: LocalDateTime) {
        val existing = confirmThrottleRepository.find(confirmerId)
        val (newCount, newWindowStart, newLock) = ConfirmThrottlePolicy.onFailure(
            failCount = existing?.failCount ?: 0,
            windowStart = existing?.windowStart,
            now = now,
        )
        confirmThrottleRepository.upsertOnFailure(confirmerId, newCount, newWindowStart, newLock, now)
        if (newLock != null) {
            log.info("confirm 失败达上限,锁定 confirmer={} lockedUntil={}", confirmerId, newLock)
        }
    }

    private fun generateCode(): String {
        repeat(5) {
            val code = (1..BindCodePolicy.CODE_LENGTH).map { random.nextInt(10) }.joinToString("")
            if (!bondRepository.existsByBindCode(code)) {
                return code
            }
        }
        throw BizException(ErrorCode.INTERNAL, HttpStatus.INTERNAL_SERVER_ERROR, "绑定码生成失败")
    }

    private fun deeplink(code: String): String = "bugu://bond?code=$code"

    private fun BondRow.toDto(viewerId: Long): BondDto {
        val counterpartId = if (requesterId == viewerId) receiverId else requesterId
        val counterpart = counterpartId?.let { userRepository.findByIdOrNull(it) }
        val sender = userRepository.findByIdOrNull(requesterId)
        val receiver = receiverId?.let { userRepository.findByIdOrNull(it) }
        return BondDto(
            id = id!!,
            status = status,
            role = if (requesterId == viewerId) "REQUESTER" else "RECEIVER",
            counterpartId = counterpartId,
            createdAt = createdAt.toString(),
            confirmedAt = confirmedAt?.toString(),
            remark = if (requesterId == viewerId) requesterRemark else receiverRemark,
            counterpartNickname = counterpart?.nickname,
            counterpartAvatarUrl = counterpart?.avatarUrl,
            senderNickname = sender?.nickname,
            receiverNickname = receiver?.nickname,
            senderAvatarUrl = sender?.avatarUrl,
            receiverAvatarUrl = receiver?.avatarUrl,
        )
    }
}
