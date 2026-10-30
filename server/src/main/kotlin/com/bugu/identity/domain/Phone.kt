package com.bugu.identity.domain

/**
 * 大陆手机号值对象。P0 仅支持 +86 段位(1[3-9] 开头 11 位)。
 */
@JvmInline
value class Phone(val value: String) {

    init {
        require(value.matches(REGEX)) { "手机号格式不正确: $value" }
    }

    companion object {
        private val REGEX = Regex("^1[3-9]\\d{9}$")

        fun of(raw: String): Phone {
            val normalized = raw.trim()
            return Phone(normalized)
        }
    }
}
