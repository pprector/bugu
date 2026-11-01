package com.bugu.client.domain

import com.bugu.client.api.BondDto
import com.bugu.client.api.ReminderView
import com.bugu.client.api.displayName

fun ReminderView.companionName(bonds: List<BondDto>): String {
    if (creatorId == receiverId) return "自己"
    val bond = if (bondId != null) {
        bonds.firstOrNull { it.id == bondId && it.counterpartId in listOf(creatorId, receiverId) }
    } else {
        bonds.filter { it.counterpartId in listOf(creatorId, receiverId) }.singleOrNull()
    }
    return bond?.displayName ?: "亲友"
}

fun ReminderView.directionLabel(bonds: List<BondDto>, byMe: Boolean): String =
    if (creatorId == receiverId) "我 → 自己"
    else if (byMe) "我 → ${companionName(bonds)}" else "${companionName(bonds)} → 我"

fun selectedReminderReceiver(bonds: List<BondDto>, bondId: Long, selfId: Long): Long? =
    if (bondId == 0L) selfId
    else bonds.firstOrNull { it.id == bondId && it.status == "ACTIVE" }?.counterpartId
