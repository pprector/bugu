package com.bugu.bond.domain

/**
 * bond 状态集(epic Requirements:pending_outbound → active → unbound,unbound 为终态)。
 */
enum class BondStatus {
    PENDING_OUTBOUND,
    ACTIVE,
    UNBOUND,
}
