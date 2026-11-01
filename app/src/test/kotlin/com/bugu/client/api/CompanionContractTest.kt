package com.bugu.client.api

import com.bugu.client.domain.companionName
import com.bugu.client.domain.directionLabel
import com.bugu.client.domain.selectedReminderReceiver
import org.junit.Test
import kotlin.test.*

class CompanionContractTest {
    private fun bond(id: Long, peer: Long, nickname: String, remark: String? = null) =
        BondDto(id, "ACTIVE", "REQUESTER", peer, "2026-10-31T00:00:00", counterpartNickname = nickname, remark = remark)

    private fun reminder(bondId: Long? = 2, creator: Long = 30, receiver: Long = 10) =
        ReminderView(7, bondId, creator, receiver, "喝水", "ONCE", "GENTLE", "2026-11-01T08:00:00", voiceEnabled = false, status = "PENDING", silent = false, createdAt = "2026-10-31T00:00:00")

    @Test fun oldAndNewBondPayloadsRemainCompatible() {
        val old = ApiJson.json.decodeFromString<BondDto>("""{"id":1,"status":"ACTIVE","role":"REQUESTER","createdAt":"2026-10-31"}""")
        assertNull(old.remark)
        assertNull(old.counterpartAvatarUrl)
        assertEquals("亲友", old.displayName)
        val new = ApiJson.json.decodeFromString<BondDto>("""{"id":2,"status":"ACTIVE","role":"RECEIVER","createdAt":"2026-10-31","remark":"私人称呼","senderNickname":"昵称","senderAvatarUrl":"/a","receiverAvatarUrl":null,"counterpartAvatarUrl":"/a","futureField":true}""")
        assertEquals("私人称呼", new.displayName)
        assertEquals("/a", new.senderAvatarUrl)
        assertEquals("昵称", new.copy(remark = "  ").displayName)
    }

    @Test fun remarkClearIncludesExplicitNullAndEnforcesLimit() {
        assertEquals("{\"remark\":null}", ApiJson.json.encodeToString(BondRemarkRequest(null).toJson()))
        assertEquals("{\"remark\":\"备注\"}", ApiJson.json.encodeToString(BondRemarkRequest("备注").toJson()))
        BondRemarkRequest("字".repeat(30))
        assertFailsWith<IllegalArgumentException> { BondRemarkRequest("字".repeat(31)) }
    }

    @Test fun namesResolvePerBondWithPrivateRemarkAndNeverUseFirstPeer() {
        val bonds = listOf(bond(1, 20, "甲"), bond(2, 30, "乙", "妈妈"))
        assertEquals("妈妈", reminder().companionName(bonds))
        assertEquals("妈妈 → 我", reminder().directionLabel(bonds, false))
        assertEquals("我 → 妈妈", reminder(creator = 10, receiver = 30).directionLabel(bonds, true))
        assertEquals("亲友", reminder(bondId = 99).companionName(bonds))
        assertEquals("亲友", reminder(bondId = 1).companionName(bonds))
        assertEquals("妈妈", reminder(bondId = null).companionName(bonds))
        assertEquals("我 → 自己", reminder(bondId = null, creator = 10).directionLabel(bonds, true))
    }

    @Test fun invalidRecipientNeverFallsBackToSelf() {
        val bonds = listOf(bond(1, 20, "甲"), bond(2, 30, "乙"))
        assertEquals(30L, selectedReminderReceiver(bonds, 2, 10))
        assertEquals(10L, selectedReminderReceiver(bonds, 0, 10))
        assertNull(selectedReminderReceiver(bonds, 99, 10))
        assertNull(selectedReminderReceiver(listOf(bonds[1].copy(status = "UNBOUND")), 2, 10))
        assertNull(selectedReminderReceiver(listOf(bonds[1].copy(counterpartId = null)), 2, 10))
    }
}
