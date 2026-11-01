package com.bugu.bond.app

import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Component

@Component
class BondLifecycle(private val jdbc: JdbcTemplate) {
    fun deactivate(bondId: Long) {
        jdbc.update(
            "UPDATE reminder SET status = 'CANCELED', updated_at = CURRENT_TIMESTAMP " +
                "WHERE bond_id = ? AND status IN ('PENDING', 'FIRED', 'SNOOZED')", bondId,
        )
        jdbc.update(
            "UPDATE reminder_fire SET state = 'CLOSED' WHERE state IN ('PENDING', 'FIRED') " +
                "AND reminder_id IN (SELECT id FROM reminder WHERE bond_id = ?)", bondId,
        )
    }
}
