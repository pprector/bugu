-- ============================================================
-- V2:身份模块(story 1.1)——手机号登录与短信限流
-- app_user 补手机号与账号类型;sms_code 按手机号一行存验证码哈希与发送限流计数
-- ============================================================

ALTER TABLE app_user
    ADD COLUMN phone        VARCHAR(20),                          -- 大陆手机号,登录标识;长辈设备账号为 NULL
    ADD COLUMN account_type VARCHAR(16) NOT NULL DEFAULT 'NORMAL', -- NORMAL / ELDER
    ADD CONSTRAINT uk_user_phone UNIQUE (phone);

-- 短信验证码与发送限流(DB 计数)
CREATE TABLE sms_code
(
    phone        VARCHAR(20)  NOT NULL PRIMARY KEY,    -- 手机号(一行一手机)
    code_hash    CHAR(64)     NOT NULL,                -- SHA-256(code),不落明文
    send_count   INT          NOT NULL DEFAULT 1,      -- 当前 60s 窗口内已发次数
    window_start TIMESTAMP(3) NOT NULL,                -- 当前计数窗口起点
    locked_until TIMESTAMP(3),                         -- 限流锁到期时刻(60s 内第 6 次触发,锁 15min)
    sent_at      TIMESTAMP(3) NOT NULL,                -- 最近一次发码时刻(验证码有效期起点)
    created_at   TIMESTAMP(3) NOT NULL,
    updated_at   TIMESTAMP(3) NOT NULL
);
