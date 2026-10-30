package com.bugu.shared.infra

import com.bugu.shared.api.BizException
import com.bugu.shared.api.ErrorCode
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Base64
import java.security.MessageDigest
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * 腾讯云 COS 对象存储配置:文件(头像等)签名直传,AK/SK 只在服务端,
 * 端侧只拿到按对象 key 精确绑定的一次性上传凭证,不持有任何长期凭证。
 */
@ConfigurationProperties(prefix = "bugu.cos")
data class CosProperties(
    val secretId: String = "",
    val secretKey: String = "",
    /** 桶所在地域,如 ap-guangzhou。 */
    val region: String = "ap-guangzhou",
    /** 桶全名,如 bugu-1250000000。桶建议公有读私有写(头像公开读,写入只认签名)。 */
    val bucket: String = "",
    /** 对外读取域名:留空 = 桶默认域名;接入 CDN 后填加速域名(如 https://cdn.example.com)。 */
    val publicBaseUrl: String = "",
    /** 上传凭证有效期(秒),过期由 COS 拒收兜底。 */
    val expireSeconds: Long = 600,
)

/**
 * COS PostObject 直传凭证:端侧把 [formFields] 原样作为表单字段、文件以 file 字段
 * 放在最后,multipart POST 到 [uploadUrl];成功(2xx)后对象即可经 [publicUrl] 公网读取。
 */
@Serializable
data class CosUploadTicket(
    val uploadUrl: String,
    val objectKey: String,
    val publicUrl: String,
    val formFields: Map<String, String>,
    val maxBytes: Long,
    val expiresInSeconds: Long,
)

/**
 * COS PostObject 服务端签名(官方「服务端签名后直传」算法):
 * KeyTime = "起;止" 秒级时间戳;SignKey = HMAC-SHA1(SecretKey, KeyTime) 的小写十六进制;
 * StringToSign = policy JSON 的 Base64;Signature = HMAC-SHA1(SignKey, StringToSign) 的小写十六进制。
 * 签名纯本地计算不发网络请求;policy 用 eq $key 把凭证精确绑定到单个对象,端侧改传其他 key 会被 COS 拒收。
 */
@Component
@EnableConfigurationProperties(CosProperties::class)
class CosUploadSigner(private val properties: CosProperties) {

    private val json = Json

    /** COS 侧统一校验:凭证缺失直接拒绝,不发放半可用凭证。 */
    fun requireConfigured() {
        if (properties.secretId.isBlank() || properties.secretKey.isBlank() || properties.bucket.isBlank()) {
            throw BizException(
                ErrorCode.INTERNAL,
                HttpStatus.INTERNAL_SERVER_ERROR,
                "对象存储(COS)未配置,请在服务端 bugu.cos 填入凭证",
            )
        }
    }

    /** 对象的公网读取地址:优先自定义域名(去尾斜杠拼接),否则桶默认域名。 */
    fun publicUrl(objectKey: String): String {
        val base = properties.publicBaseUrl.trim().trimEnd('/').ifBlank {
            "https://${properties.bucket}.cos.${properties.region}.myqcloud.com"
        }
        return "$base/$objectKey"
    }

    /**
     * 为指定对象签发直传凭证;maxBytes 经 policy 的 content-length-range 由 COS 强制执行。
     * 算法遵循 COS PostObject 官方「签名保护」(勿与 AWS S3 混淆):
     * SignKey = HMAC-SHA1(SecretKey, KeyTime) 小写十六进制;
     * StringToSign = SHA1(policy 原文文本) 小写十六进制(不是 Base64(policy));
     * Signature = HMAC-SHA1(SignKey 字符串, StringToSign 字符串) 小写十六进制。
     * 签名纯本地计算不发网络请求;policy 用 eq $key 把凭证精确绑定到单个对象,端侧改传其他 key 会被 COS 拒收。
     */
    fun sign(objectKey: String, maxBytes: Long, now: Instant): CosUploadTicket {
        requireConfigured()
        val expiresAt = now.plusSeconds(properties.expireSeconds)
        val keyTime = "${now.epochSecond};${expiresAt.epochSecond}"
        val policyText = buildPolicy(objectKey, maxBytes, keyTime, expiresAt)
        val policyBase64 = Base64.getEncoder().encodeToString(policyText.toByteArray())
        val signKey = hmacSha1Hex(properties.secretKey.toByteArray(), keyTime)
        val signature = hmacSha1Hex(signKey.toByteArray(), sha1Hex(policyText))
        return CosUploadTicket(
            uploadUrl = "https://${properties.bucket}.cos.${properties.region}.myqcloud.com/",
            objectKey = objectKey,
            publicUrl = publicUrl(objectKey),
            formFields = mapOf(
                "key" to objectKey,
                "policy" to policyBase64,
                "q-sign-algorithm" to "sha1",
                "q-ak" to properties.secretId,
                "q-key-time" to keyTime,
                "q-signature" to signature,
                // 缺省 204;显式要求 200,端侧按 isSuccess 判定更直白
                "success_action_status" to "200",
            ),
            maxBytes = maxBytes,
            expiresInSeconds = properties.expireSeconds,
        )
    }

    /** policy JSON(紧凑序列化):绑桶 + 绑签名要素(算法/AK/时间)+ 绑 key + 限大小,签名与表单发送的是同一串字节。 */
    private fun buildPolicy(objectKey: String, maxBytes: Long, keyTime: String, expiresAt: Instant): String =
        json.encodeToString(
            buildJsonObject {
                put("expiration", ISO_MILLIS.format(expiresAt))
                put(
                    "conditions",
                    buildJsonArray {
                        add(buildJsonObject { put("bucket", properties.bucket) })
                        // COS 强制要求:policy 条件里必须显式列出 q-sign-algorithm / q-ak / q-sign-time,
                        // 缺任一项直接 InvalidPolicyDocument(注意条件里是 q-sign-time,表单里才是 q-key-time)
                        add(buildJsonObject { put("q-sign-algorithm", "sha1") })
                        add(buildJsonObject { put("q-ak", properties.secretId) })
                        add(buildJsonObject { put("q-sign-time", keyTime) })
                        add(buildJsonArray {
                            add(JsonPrimitive("eq"))
                            add(JsonPrimitive("\$key"))
                            add(JsonPrimitive(objectKey))
                        })
                        add(buildJsonArray {
                            add(JsonPrimitive("content-length-range"))
                            add(JsonPrimitive(1))
                            add(JsonPrimitive(maxBytes))
                        })
                    },
                )
            },
        )

    private fun hmacSha1Hex(key: ByteArray, data: String): String {
        val mac = Mac.getInstance("HmacSHA1")
        mac.init(SecretKeySpec(key, "HmacSHA1"))
        return mac.doFinal(data.toByteArray())
            .joinToString("") { (it.toInt() and 0xff).toString(16).padStart(2, '0') }
    }

    private fun sha1Hex(data: String): String =
        MessageDigest.getInstance("SHA1").digest(data.toByteArray())
            .joinToString("") { (it.toInt() and 0xff).toString(16).padStart(2, '0') }

    companion object {
        /** COS 要求 ISO8601 毫秒精度(2026-10-29T08:00:00.000Z)。 */
        private val ISO_MILLIS: DateTimeFormatter =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'").withZone(ZoneOffset.UTC)
    }
}
