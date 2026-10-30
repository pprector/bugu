-- ============================================================
-- V3:bond 模块(story 1.2)——绑定码发起与接受
-- receiver_id 改可空(PENDING_OUTBOUND 期间接收人未知);
-- 状态集收敛为 PENDING_OUTBOUND / ACTIVE / UNBOUND;
-- 新增绑定码及其过期时刻,确认成功后清空(一次性)
-- ============================================================

ALTER TABLE bond
    ALTER COLUMN receiver_id DROP NOT NULL,            -- 接收人,PENDING_OUTBOUND 期间为 NULL,确认时回填
    ALTER COLUMN status SET DEFAULT 'PENDING_OUTBOUND', -- PENDING_OUTBOUND / ACTIVE / UNBOUND
    ADD COLUMN bind_code       CHAR(6),                -- 绑定码,PENDING_OUTBOUND 期间唯一
    ADD COLUMN code_expires_at TIMESTAMP(3),           -- 绑定码过期时刻
    ADD CONSTRAINT uk_bond_bind_code UNIQUE (bind_code);
