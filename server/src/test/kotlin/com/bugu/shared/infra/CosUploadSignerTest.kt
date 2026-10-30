package com.bugu.shared.infra

import com.bugu.shared.api.BizException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.Base64
import java.security.MessageDigest
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * COS 签名单元测试:按官方「服务端签名后直传」算法独立复算签名,
 * 校验 policy 结构、keyTime 格式与公网地址拼接。纯本地计算,无网络。
 */
class CosUploadSignerTest {

    private val properties = CosProperties(
        secretId = "AKIDtest",
        secretKey = "secret-test",
        region = "ap-guangzhou",
        bucket = "bugu-1250000000",
    )
    private val signer = CosUploadSigner(properties)

    @Test
    fun `签名按官方算法可独立复算`() {
        val now = Instant.parse("2026-10-29T08:00:00Z")
        val ticket = signer.sign("avatar/1/1.jpg", AVATAR_MAX_BYTES, now)

        val keyTime = ticket.formFields.getValue("q-key-time")
        assertEquals("${now.epochSecond};${now.plusSeconds(600).epochSecond}", keyTime)

        // 官方算法:SignKey = HMAC-SHA1(SecretKey, KeyTime);
        // StringToSign = SHA1(policy 原文);Signature = HMAC-SHA1(SignKey 字符串, StringToSign)
        val signKey = hmacSha1Hex(properties.secretKey.toByteArray(), keyTime)
        val policyText = String(Base64.getDecoder().decode(ticket.formFields.getValue("policy")))
        val stringToSign = MessageDigest.getInstance("SHA1").digest(policyText.toByteArray())
            .joinToString("") { (it.toInt() and 0xff).toString(16).padStart(2, '0') }
        val expected = hmacSha1Hex(signKey.toByteArray(), stringToSign)
        assertEquals(expected, ticket.formFields.getValue("q-signature"))
        assertEquals("sha1", ticket.formFields.getValue("q-sign-algorithm"))
        assertEquals("AKIDtest", ticket.formFields.getValue("q-ak"))
        assertEquals("avatar/1/1.jpg", ticket.formFields.getValue("key"))
    }

    @Test
    fun `policy 绑桶绑 key 限大小`() {
        val now = Instant.parse("2026-10-29T08:00:00Z")
        val ticket = signer.sign("avatar/3/123.png", 2048, now)

        val policy = Json.parseToJsonElement(
            Base64.getDecoder().decode(ticket.formFields.getValue("policy")).decodeToString(),
        ).jsonObject
        assertEquals("2026-10-29T08:10:00.000Z", policy.getValue("expiration").jsonPrimitive.content)

        val conditions = policy.getValue("conditions").jsonArray
        assertEquals("bugu-1250000000", conditions[0].jsonObject.getValue("bucket").jsonPrimitive.content)
        // COS 强制要求:q-sign-algorithm / q-ak / q-sign-time 三条件缺一不可
        assertEquals("sha1", conditions[1].jsonObject.getValue("q-sign-algorithm").jsonPrimitive.content)
        assertEquals("AKIDtest", conditions[2].jsonObject.getValue("q-ak").jsonPrimitive.content)
        assertEquals(ticket.formFields.getValue("q-key-time"), conditions[3].jsonObject.getValue("q-sign-time").jsonPrimitive.content)
        assertEquals(listOf("eq", "\$key", "avatar/3/123.png"), conditions[4].jsonArray.map { it.jsonPrimitive.content })
        assertEquals(listOf("content-length-range", "1", "2048"), conditions[5].jsonArray.map { it.jsonPrimitive.content })
    }

    @Test
    fun `公网地址默认桶域名可被自定义域名覆盖`() {
        assertEquals(
            "https://bugu-1250000000.cos.ap-guangzhou.myqcloud.com/avatar/1/1.jpg",
            signer.publicUrl("avatar/1/1.jpg"),
        )

        val cdn = CosUploadSigner(properties.copy(publicBaseUrl = "https://cdn.example.com/"))
        assertEquals("https://cdn.example.com/avatar/1/1.jpg", cdn.publicUrl("avatar/1/1.jpg"))
    }

    @Test
    fun `凭证缺失直接拒绝`() {
        val blank = CosUploadSigner(CosProperties())
        val exception = assertThrows(BizException::class.java) {
            blank.sign("avatar/1/1.jpg", 2048, Instant.now())
        }
        assertTrue(exception.message!!.contains("未配置"))
    }

    private fun hmacSha1Hex(key: ByteArray, data: String): String {
        val mac = Mac.getInstance("HmacSHA1")
        mac.init(SecretKeySpec(key, "HmacSHA1"))
        return mac.doFinal(data.toByteArray())
            .joinToString("") { (it.toInt() and 0xff).toString(16).padStart(2, '0') }
    }

    private companion object {
        const val AVATAR_MAX_BYTES = 2L * 1024 * 1024
    }
}
