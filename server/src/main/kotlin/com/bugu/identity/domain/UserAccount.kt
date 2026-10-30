package com.bugu.identity.domain

import java.time.LocalDateTime

/**
 * 用户账号聚合:资料与账号类型的状态变更只走聚合方法。
 * accountState(启用/禁用)的变更在后续故事引入,此处仅承载资料与升级。
 *
 * 不变式:
 * - ELDER 账号:phone 为 null,deviceUuid 非空;升级为 NORMAL 后 phone 回填
 */
class UserAccount(
    val id: Long?,
    nickname: String,
    avatarUrl: String?,
    phone: String?,
    val deviceUuid: String?,
    accountType: String,
    elderMode: Boolean,
    val accountState: String,
    val createdAt: LocalDateTime,
) {

    var nickname: String = nickname
        private set

    var avatarUrl: String? = avatarUrl
        private set

    var phone: String? = phone
        private set

    var accountType: String = accountType
        private set

    var elderMode: Boolean = elderMode
        private set

    /** 子女端标记/取消长辈模式(FR-06):长辈模式账号仅见极简界面。 */
    fun setElderMode(enabled: Boolean) {
        elderMode = enabled
    }

    /** 代填资料:仅更新传入的非空字段。 */
    fun updateProfile(nickname: String?, avatarUrl: String?) {
        nickname?.let {
            check(it.isNotBlank()) { "昵称不能为空" }
            this.nickname = it.take(32)
        }
        avatarUrl?.let { this.avatarUrl = it.take(512) }
    }

    /** 长辈账号绑定手机号,升级为正式账号并退出长辈模式。 */
    fun upgradeToNormal(phone: String) {
        check(accountType == "ELDER") { "仅长辈账号可升级: $accountType" }
        check(this.phone == null) { "已绑定手机号" }
        this.phone = phone
        accountType = "NORMAL"
        elderMode = false
    }
}
