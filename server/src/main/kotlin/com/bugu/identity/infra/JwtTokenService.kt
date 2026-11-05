package com.bugu.identity.infra

import io.jsonwebtoken.Jwts
import io.jsonwebtoken.JwtException
import io.jsonwebtoken.security.Keys
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.stereotype.Component
import java.time.Duration
import java.time.Instant
import java.util.Date
import javax.crypto.SecretKey

/**
 * JWT 配置。secret 生产环境经 compose 环境变量 BUGU_JWT_SECRET 覆盖(≥32 字节)。
 */
@ConfigurationProperties(prefix = "bugu.jwt")
data class JwtProperties(
    val secret: String,
    val expireDays: Long = 7,
)

/**
 * HS256 签发与解析。P0 无 refresh 轮换:过期即重新走验证码登录。
 * E9 双体系互斥:App token 与后台 token(typ=admin)不互通——
 * parseUserId 拒后台 token、parseAdminId 拒 App token,跨体系一律视为非法。
 * 吊销:token 内 ver 与账号 token_version 不一致即失效(旧 token 无 ver 视为 0,兼容存量会话)。
 */
@Component
class JwtTokenService(properties: JwtProperties) {

    init {
        require(properties.secret.length >= 32) {
            "JWT 密钥仅 ${properties.secret.length} 字节,不足 32 字节:请通过环境变量 BUGU_JWT_SECRET 注入随机串"
        }
    }

    private val key: SecretKey = Keys.hmacShaKeyFor(properties.secret.toByteArray(Charsets.UTF_8))
    private val ttl: Duration = Duration.ofDays(properties.expireDays)

    fun issue(userId: Long, phone: String?, tokenVersion: Int = 0): String {
        val now = Instant.now()
        return Jwts.builder()
            .subject(userId.toString())
            .claim("phone", phone)
            .claim(VER_CLAIM, tokenVersion)
            .issuedAt(Date.from(now))
            .expiration(Date.from(now.plus(ttl)))
            .signWith(key)
            .compact()
    }

    /** 后台 token(E9):typ=admin,与 App token 不互通。 */
    fun issueAdmin(adminUserId: Long, tokenVersion: Int = 0): String {
        val now = Instant.now()
        return Jwts.builder()
            .subject(adminUserId.toString())
            .claim(TYP_CLAIM, TYP_ADMIN)
            .claim(VER_CLAIM, tokenVersion)
            .issuedAt(Date.from(now))
            .expiration(Date.from(now.plus(ttl)))
            .signWith(key)
            .compact()
    }

    /** 校验签名与有效期,返回 userId;非法、过期或后台 token 抛 [JwtException]。 */
    @Throws(JwtException::class)
    fun parseUserId(token: String): Long = parseUserSubject(token).id

    /** 校验签名与有效期,返回 userId 与 token 版本(供吊销校验)。 */
    @Throws(JwtException::class)
    fun parseUserSubject(token: String): TokenSubject {
        val claims = parse(token)
        if (claims[TYP_CLAIM] == TYP_ADMIN) {
            throw JwtException("admin token 不适用于 App 端点")
        }
        return TokenSubject(claims.subject.toLong(), versionOf(claims[VER_CLAIM]))
    }

    /** 后台 token 校验,返回 adminUserId;非法、过期或 App token 抛 [JwtException]。 */
    @Throws(JwtException::class)
    fun parseAdminId(token: String): Long = parseAdminSubject(token).id

    /** 后台 token 校验,返回 adminUserId 与 token 版本(供吊销校验)。 */
    @Throws(JwtException::class)
    fun parseAdminSubject(token: String): TokenSubject {
        val claims = parse(token)
        if (claims[TYP_CLAIM] != TYP_ADMIN) {
            throw JwtException("App token 不适用于后台端点")
        }
        return TokenSubject(claims.subject.toLong(), versionOf(claims[VER_CLAIM]))
    }

    private fun versionOf(raw: Any?): Int = (raw as? Number)?.toInt() ?: 0

    private fun parse(token: String) = Jwts.parser()
        .verifyWith(key)
        .build()
        .parseSignedClaims(token)
        .payload

    private companion object {
        const val TYP_CLAIM = "typ"
        const val TYP_ADMIN = "admin"
        const val VER_CLAIM = "ver"
    }
}

/** token 主体:账号 id 与签发时的吊销版本。 */
data class TokenSubject(val id: Long, val tokenVersion: Int = 0)
