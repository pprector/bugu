package com.bugu.identity.app

import com.bugu.identity.api.LoginResponse
import com.bugu.identity.api.UserDto
import com.bugu.identity.domain.AvatarPolicy
import com.bugu.identity.domain.Phone
import com.bugu.identity.domain.SmsCodePolicy
import com.bugu.identity.infra.JwtTokenService
import com.bugu.identity.infra.SmsCodeRepository
import com.bugu.identity.infra.SmsCodeRow
import com.bugu.identity.infra.SmsSender
import com.bugu.identity.infra.UserRepository
import com.bugu.identity.infra.UserRow
import com.bugu.shared.api.BizException
import com.bugu.shared.api.ErrorCode
import com.bugu.shared.infra.CosUploadSigner
import com.bugu.shared.infra.CosUploadTicket
import org.slf4j.LoggerFactory
import org.springframework.dao.DuplicateKeyException
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.data.repository.findByIdOrNull
import org.springframework.transaction.support.TransactionTemplate
import java.security.SecureRandom
import java.time.Clock
import java.time.LocalDateTime

/**
 * 身份应用服务:发码(限流)、验证码合一注册登录、长辈账号绑手机号升级。
 * 时间一律经 [Clock] 注入,策略判断在 domain 纯函数中完成。
 */
@Service
class AuthService(
    private val smsCodeRepository: SmsCodeRepository,
    private val userRepository: UserRepository,
    private val userService: UserService,
    private val smsSender: SmsSender,
    private val jwtTokenService: JwtTokenService,
    private val cosUploadSigner: CosUploadSigner,
    private val clock: Clock,
    private val transactionTemplate: TransactionTemplate,
) {

    private val log = LoggerFactory.getLogger(javaClass)
    private val random = SecureRandom()

    fun sendCode(phone: Phone) {
        val now = LocalDateTime.now(clock)
        val existing = smsCodeRepository.find(phone.value)

        when (val decision = SmsCodePolicy.evaluateSend(
            sendCount = existing?.sendCount ?: 0,
            windowStart = existing?.windowStart,
            lockedUntil = existing?.lockedUntil,
            now = now,
        )) {
            is SmsCodePolicy.SendDecision.Denied -> {
                // 锁必须落库:否则 60s 窗口一过计数清零,限流即被绕过
                smsCodeRepository.lock(phone.value, decision.lockedUntil, now)
                throw BizException(ErrorCode.SMS_RATE_LIMITED, HttpStatus.TOO_MANY_REQUESTS)
            }

            is SmsCodePolicy.SendDecision.Allowed -> {
                val code = generateCode()
                smsCodeRepository.upsertOnSend(
                    SmsCodeRow(
                        phone = phone.value,
                        codeHash = SmsCodePolicy.hash(code),
                        sendCount = decision.newCount,
                        windowStart = decision.windowStart,
                        lockedUntil = null,
                        sentAt = now,
                    ),
                    now,
                )
                smsSender.send(phone.value, code)
                log.debug("验证码已发送 phone=***{} count={}", phone.value.takeLast(4), decision.newCount)
            }
        }
    }

    fun login(phone: Phone, code: String): LoginResponse {
        val now = LocalDateTime.now(clock)
        val row = smsCodeRepository.find(phone.value)
        if (row == null || !SmsCodePolicy.isCodeValid(row.codeHash, row.sentAt, code, now)) {
            throw BizException(ErrorCode.UNAUTHORIZED, HttpStatus.UNAUTHORIZED, "验证码错误或已过期")
        }

        return transactionTemplate.execute {
            smsCodeRepository.delete(phone.value)
            val user = userRepository.findByPhone(phone.value) ?: userRepository.save(
                UserRow(
                    nickname = "用户" + phone.value.takeLast(4),
                    phone = phone.value,
                ),
            )
            val token = jwtTokenService.issue(user.id!!, user.phone)
            LoginResponse(token = token, user = userService.toDto(user))
        }
    }

    fun me(userId: Long): UserDto {
        val user = userRepository.findByIdOrNull(userId)
            ?: throw BizException(ErrorCode.UNAUTHORIZED, HttpStatus.UNAUTHORIZED)
        return userService.toDto(user)
    }

    /**
     * 编辑个人资料:仅更新传入的非空字段,至少传一项;昵称给就须 1-32 字。
     * 在这里校验而非依赖聚合内的静默截断/异常——端侧需要明确的参数错误信号。
     */
    fun updateProfile(userId: Long, nickname: String?, avatarUrl: String?): UserDto {
        if (nickname == null && avatarUrl == null) {
            throw BizException(ErrorCode.INVALID_ARGUMENT, HttpStatus.BAD_REQUEST, "至少需要提供一项要更新的字段")
        }
        if (nickname != null && (nickname.isBlank() || nickname.length > 32)) {
            throw BizException(ErrorCode.INVALID_ARGUMENT, HttpStatus.BAD_REQUEST, "昵称需为 1-32 个字符")
        }
        val updated = try {
            userService.updateProfile(userId, nickname, avatarUrl)
        } catch (e: IllegalArgumentException) {
            throw BizException(ErrorCode.NOT_FOUND, HttpStatus.NOT_FOUND, "用户不存在")
        }
        return userService.toDto(updated)
    }

    /**
     * 头像直传第一步:校验类型白名单 → 生成用户专属对象 key(带时间戳,重传天然换 URL
     * 不踩 CDN 缓存)→ COS 签名。大小上限写入凭证 policy,由 COS 侧强制执行。
     */
    fun issueAvatarUploadTicket(userId: Long, contentType: String?): CosUploadTicket {
        val extension = when (val decision = AvatarPolicy.evaluate(contentType)) {
            is AvatarPolicy.Decision.Rejected ->
                throw BizException(ErrorCode.INVALID_ARGUMENT, HttpStatus.BAD_REQUEST, decision.reason)
            is AvatarPolicy.Decision.Allowed -> decision.extension
        }
        val objectKey = "avatar/$userId/${clock.instant().toEpochMilli()}.$extension"
        return cosUploadSigner.sign(objectKey, AvatarPolicy.MAX_BYTES, clock.instant())
    }

    /**
     * 头像直传第二步:确认落库。objectKey 必须匹配凭证下发的形状(avatar/用户id/毫秒.扩展名),
     * 先整体形状校验再校验属主前缀,杜绝改传他人对象或任意 URL 注入。
     */
    fun confirmAvatarUpload(userId: Long, objectKey: String?): UserDto {
        if (objectKey.isNullOrBlank() || !AVATAR_KEY_PATTERN.matches(objectKey)) {
            throw BizException(ErrorCode.INVALID_ARGUMENT, HttpStatus.BAD_REQUEST, "头像对象地址不合法")
        }
        if (!objectKey.startsWith("avatar/$userId/")) {
            throw BizException(ErrorCode.FORBIDDEN, HttpStatus.FORBIDDEN, "不能修改其他用户的头像")
        }
        return userService.toDto(userService.updateProfile(userId, null, cosUploadSigner.publicUrl(objectKey)))
    }

    private companion object {
        val AVATAR_KEY_PATTERN = Regex("^avatar/\\d+/\\d+\\.(jpg|png|webp)$")
    }

    /**
     * 长辈账号绑定手机号升级正式账号:复用短信验证码校验,验证码一次性;
     * 手机号被占用返回 CONFLICT。占用检查与写入存在并发窗口,由 uk_user_phone
     * 唯一索引兜底——冲突时 DuplicateKeyException 在此同样转为 CONFLICT。
     */
    fun bindPhone(userId: Long, phone: Phone, code: String): UserDto {
        val now = LocalDateTime.now(clock)
        val row = smsCodeRepository.find(phone.value)
        if (row == null || !SmsCodePolicy.isCodeValid(row.codeHash, row.sentAt, code, now)) {
            throw BizException(ErrorCode.UNAUTHORIZED, HttpStatus.UNAUTHORIZED, "验证码错误或已过期")
        }
        return transactionTemplate.execute {
            if (userRepository.existsByPhone(phone.value)) {
                throw BizException(ErrorCode.CONFLICT, HttpStatus.CONFLICT, "该手机号已被其他账号使用")
            }
            smsCodeRepository.delete(phone.value)
            try {
                userService.toDto(userService.bindPhone(userId, phone.value))
            } catch (e: DuplicateKeyException) {
                throw BizException(ErrorCode.CONFLICT, HttpStatus.CONFLICT, "该手机号已被其他账号使用")
            }
        }
    }

    private fun generateCode(): String =
        (1..SmsCodePolicy.CODE_LENGTH)
            .map { random.nextInt(10) }
            .joinToString("")
}
