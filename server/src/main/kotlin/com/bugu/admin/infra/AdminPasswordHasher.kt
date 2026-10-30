package com.bugu.admin.infra

import java.security.SecureRandom
import java.util.Base64
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * 后台口令哈希(E9):PBKDF2WithHmacSHA256,自包含格式
 * `pbkdf2:<iterations>:<saltB64>:<hashB64>`,盐随机,哈希不进 git。
 */
object AdminPasswordHasher {

    private const val ITERATIONS = 120_000
    private const val KEY_BITS = 256
    private const val SALT_BYTES = 16
    private const val ALGORITHM = "PBKDF2WithHmacSHA256"

    fun hash(raw: String): String {
        val salt = ByteArray(SALT_BYTES).also { SecureRandom().nextBytes(it) }
        val hash = pbkdf2(raw, salt, ITERATIONS)
        return "pbkdf2:$ITERATIONS:${Base64.getEncoder().encodeToString(salt)}:${Base64.getEncoder().encodeToString(hash)}"
    }

    fun verify(raw: String, stored: String): Boolean {
        val parts = stored.split(":")
        if (parts.size != 4 || parts[0] != "pbkdf2") return false
        val iterations = parts[1].toIntOrNull() ?: return false
        val salt = runCatching { Base64.getDecoder().decode(parts[2]) }.getOrNull() ?: return false
        val expected = runCatching { Base64.getDecoder().decode(parts[3]) }.getOrNull() ?: return false
        val actual = pbkdf2(raw, salt, iterations)
        return actual.size == expected.size && actual.indices.all { actual[it] == expected[it] }
    }

    private fun pbkdf2(raw: String, salt: ByteArray, iterations: Int): ByteArray =
        SecretKeyFactory.getInstance(ALGORITHM)
            .generateSecret(PBEKeySpec(raw.toCharArray(), salt, iterations, KEY_BITS))
            .encoded
}
