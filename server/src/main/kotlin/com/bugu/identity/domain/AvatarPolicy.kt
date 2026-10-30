package com.bugu.identity.domain

/**
 * 头像上传策略(纯函数):类型白名单 + 大小上限。
 * 直传模式下服务端签发凭证时只知类型,大小由凭证 policy 的 content-length-range
 * 在 COS 侧强制执行(端侧也提前校验给友好提示),此处校验类型白名单。
 */
object AvatarPolicy {

    /** 单张头像上限 2MB:写入 COS 上传凭证,由存储侧拒收超限文件。 */
    const val MAX_BYTES = 2L * 1024 * 1024

    /** 允许的图片类型与对象扩展名。 */
    private val CONTENT_TYPE_EXT = mapOf(
        "image/jpeg" to "jpg",
        "image/png" to "png",
        "image/webp" to "webp",
    )

    /** 校验结果:合法给扩展名,非法给拒绝原因(直接透给客户端)。 */
    sealed class Decision {
        data class Allowed(val extension: String) : Decision()
        data class Rejected(val reason: String) : Decision()
    }

    fun evaluate(contentType: String?): Decision {
        val extension = contentType?.lowercase()?.trim()?.let { CONTENT_TYPE_EXT[it] }
            ?: return Decision.Rejected("仅支持 JPG/PNG/WebP 图片")
        return Decision.Allowed(extension)
    }
}
