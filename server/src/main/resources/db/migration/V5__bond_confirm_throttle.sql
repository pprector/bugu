-- ============================================================
-- V5:绑定码确认防爆破(story 1.6)——确认失败限流
-- 6 位码 10 分钟有效,confirm 无限制可被暴力枚举;
-- 按确认方用户一行计数,窗口内连续失败超限即锁定(DB 计数,不用 Redis)
-- ============================================================

-- 绑定码确认失败限流(DB 计数)
CREATE TABLE bond_confirm_throttle
(
    user_id      BIGINT       NOT NULL PRIMARY KEY,    -- 确认方用户 id(一行一用户)
    fail_count   INT          NOT NULL DEFAULT 0,      -- 当前窗口内确认失败次数
    window_start TIMESTAMP(3) NOT NULL,                -- 当前计数窗口起点
    locked_until TIMESTAMP(3),                         -- 限流锁到期时刻(窗口内失败超限触发)
    created_at   TIMESTAMP(3) NOT NULL,
    updated_at   TIMESTAMP(3) NOT NULL
);
