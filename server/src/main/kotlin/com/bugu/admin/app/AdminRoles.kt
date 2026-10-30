package com.bugu.admin.app

/**
 * 后台角色与权限点(E9):RBAC 从简——角色单列,映射在代码内,不建三表。
 * SUPER 全量;OPS 只读(缺 admin:write,写操作 403)。
 */
object AdminRoles {

    const val SUPER = "SUPER"
    const val OPS = "OPS"

    /** 写操作权限点:全部变更类端点要求。 */
    const val PERM_ADMIN_WRITE = "admin:write"

    /** 角色 → 权限点集合。 */
    fun permsOf(role: String): Set<String> = when (role) {
        SUPER -> setOf(PERM_ADMIN_WRITE)
        OPS -> emptySet()
        else -> emptySet()
    }
}
