-- ============================================================
-- 「布谷」基线 schema v1(PostgreSQL 方言)
-- 对应 PRD v0.2 的共享提醒模型:我创建 -> TA 送达 -> TA 确认 -> 回执
-- 时间统一 TIMESTAMP(3),由应用侧写入
-- ============================================================

-- 用户
CREATE TABLE app_user
(
    id            BIGSERIAL   PRIMARY KEY,
    nickname      VARCHAR(32) NOT NULL,                  -- 昵称,长辈端大字展示
    avatar_url    VARCHAR(512),                          -- 头像地址
    elder_mode    BOOLEAN     NOT NULL DEFAULT FALSE,    -- 长辈极简模式(FR-06)
    account_state VARCHAR(16) NOT NULL DEFAULT 'ACTIVE', -- ACTIVE / DISABLED
    created_at    TIMESTAMP(3) NOT NULL,
    updated_at    TIMESTAMP(3) NOT NULL
);

-- 设备与推送令牌
CREATE TABLE device_token
(
    id             BIGSERIAL PRIMARY KEY,
    user_id        BIGINT       NOT NULL,
    platform       VARCHAR(16)  NOT NULL,                -- ANDROID / IOS / HARMONY
    vendor         VARCHAR(32),                          -- HUAWEI / XIAOMI / OPPO / VIVO / HONOR / APNS / HARMONY_PUSH
    push_token     VARCHAR(512) NOT NULL,
    push_enabled   BOOLEAN      NOT NULL DEFAULT TRUE,
    last_active_at TIMESTAMP(3),
    created_at     TIMESTAMP(3) NOT NULL,
    updated_at     TIMESTAMP(3) NOT NULL,
    CONSTRAINT uk_push_token UNIQUE (push_token),
    CONSTRAINT fk_device_user FOREIGN KEY (user_id) REFERENCES app_user (id)
);
CREATE INDEX idx_device_user ON device_token (user_id);

-- 绑定关系(1v1,FR-01)
CREATE TABLE bond
(
    id           BIGSERIAL PRIMARY KEY,
    requester_id BIGINT       NOT NULL,                -- 发起人
    receiver_id  BIGINT       NOT NULL,                -- 接收人
    status       VARCHAR(16)  NOT NULL DEFAULT 'PENDING', -- PENDING / ACTIVE / REJECTED / UNBOUND
    confirmed_at TIMESTAMP(3),                         -- 双向同意时刻
    unbound_at   TIMESTAMP(3),                         -- 解绑时刻,解绑即时生效
    created_at   TIMESTAMP(3) NOT NULL,
    updated_at   TIMESTAMP(3) NOT NULL,
    CONSTRAINT fk_bond_requester FOREIGN KEY (requester_id) REFERENCES app_user (id),
    CONSTRAINT fk_bond_receiver FOREIGN KEY (receiver_id) REFERENCES app_user (id)
);
CREATE INDEX idx_bond_requester ON bond (requester_id);
CREATE INDEX idx_bond_receiver ON bond (receiver_id);

-- 提醒定义
CREATE TABLE reminder
(
    id            BIGSERIAL PRIMARY KEY,
    bond_id       BIGINT       NOT NULL,
    creator_id    BIGINT       NOT NULL,               -- 发起人
    receiver_id   BIGINT       NOT NULL,               -- 接收人
    title         VARCHAR(40)  NOT NULL,               -- PRD 约束:≤20 字
    kind          VARCHAR(16)  NOT NULL,               -- ONCE / REPEAT / MUTUAL_ALARM
    tone          VARCHAR(16)  NOT NULL DEFAULT 'GENTLE', -- GENTLE / NAG / STRICT
    fire_at       TIMESTAMP(3) NOT NULL,               -- 计划触发时刻
    repeat_rule   VARCHAR(64),                         -- 重复规则
    voice_enabled BOOLEAN      NOT NULL DEFAULT FALSE, -- 是否语音播报
    note          VARCHAR(200),                        -- 备注,仅端侧展示,不上云做 AI 输入
    status        VARCHAR(16)  NOT NULL DEFAULT 'PENDING', -- PENDING / FIRED / ACKED / SNOOZED / DECLINED / MISSED
    created_at    TIMESTAMP(3) NOT NULL,
    updated_at    TIMESTAMP(3) NOT NULL,
    CONSTRAINT fk_reminder_bond FOREIGN KEY (bond_id) REFERENCES bond (id),
    CONSTRAINT fk_reminder_creator FOREIGN KEY (creator_id) REFERENCES app_user (id),
    CONSTRAINT fk_reminder_receiver FOREIGN KEY (receiver_id) REFERENCES app_user (id)
);
CREATE INDEX idx_reminder_receiver ON reminder (receiver_id, status);
CREATE INDEX idx_reminder_bond ON reminder (bond_id);
CREATE INDEX idx_reminder_fire_at ON reminder (fire_at);

-- 触发记录(一次触发,升级链唯一事实源)
CREATE TABLE reminder_fire
(
    id              BIGSERIAL PRIMARY KEY,
    reminder_id     BIGINT       NOT NULL,
    fire_at         TIMESTAMP(3) NOT NULL,             -- 本次计划触发时刻
    reported_at     TIMESTAMP(3),                      -- 端侧上报时刻,断网时恢复后补报
    source          VARCHAR(16),                       -- LOCAL_ALARM / PUSH
    state           VARCHAR(16)  NOT NULL DEFAULT 'PENDING', -- PENDING / RESPONDED / CLOSED
    idempotency_key VARCHAR(96)  NOT NULL,             -- reminderId:fireAt,端侧上报幂等键
    created_at      TIMESTAMP(3) NOT NULL,
    CONSTRAINT uk_fire_idem UNIQUE (idempotency_key),
    CONSTRAINT fk_fire_reminder FOREIGN KEY (reminder_id) REFERENCES reminder (id)
);
CREATE INDEX idx_fire_state ON reminder_fire (state, fire_at);
CREATE INDEX idx_fire_reminder ON reminder_fire (reminder_id);

-- 响应记录(FR-03 三出口)
CREATE TABLE reminder_ack
(
    id             BIGSERIAL PRIMARY KEY,
    fire_id        BIGINT       NOT NULL,
    action         VARCHAR(16)  NOT NULL,              -- DONE / SNOOZE / DECLINE
    reason_code    VARCHAR(16),                        -- BUSY / CANNOT / FORGOT / OTHER
    snooze_minutes INT,                                -- 稍后提醒间隔
    acked_at       TIMESTAMP(3) NOT NULL,
    CONSTRAINT uk_ack_fire UNIQUE (fire_id),
    CONSTRAINT fk_ack_fire FOREIGN KEY (fire_id) REFERENCES reminder_fire (id)
);

-- 升级链日志(T1 / T2 / 24h 收口)
CREATE TABLE escalation_log
(
    id          BIGSERIAL PRIMARY KEY,
    fire_id     BIGINT      NOT NULL,
    stage       VARCHAR(16) NOT NULL,                  -- T1 / T2 / CLOSE
    action      VARCHAR(32) NOT NULL,                  -- REPUSH_RECEIVER / NOTIFY_CREATOR / MARK_MISSED
    push_result VARCHAR(32),                           -- ACCEPTED / FAILED / SKIPPED
    created_at  TIMESTAMP(3) NOT NULL,
    CONSTRAINT fk_escalation_fire FOREIGN KEY (fire_id) REFERENCES reminder_fire (id)
);
CREATE INDEX idx_escalation_fire ON escalation_log (fire_id);

-- AI 生成日志,成本与降级观测(FR-08)
CREATE TABLE ai_gen_log
(
    id            BIGSERIAL PRIMARY KEY,
    scene         VARCHAR(32) NOT NULL,                -- PARSE / COPY / WAKEUP
    tone          VARCHAR(16),
    degraded      BOOLEAN     NOT NULL DEFAULT FALSE,  -- 是否落入降级模板库
    latency_ms    INT,
    prompt_tokens INT,
    output_tokens INT,
    created_at    TIMESTAMP(3) NOT NULL
);
CREATE INDEX idx_ai_scene_created ON ai_gen_log (scene, created_at);

-- 降级文案模板库(FR-05)
CREATE TABLE copy_template
(
    id         BIGSERIAL PRIMARY KEY,
    tone       VARCHAR(16) NOT NULL,                   -- GENTLE / NAG / STRICT
    scene      VARCHAR(32) NOT NULL,                   -- CREATE / REPUSH / WAKEUP
    content    VARCHAR(80) NOT NULL,                   -- ≤40 字
    enabled    BOOLEAN     NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP(3) NOT NULL
);
CREATE INDEX idx_template_pick ON copy_template (tone, scene, enabled);

-- ShedLock 分布式锁表(多副本调度防重)
CREATE TABLE shedlock
(
    name       VARCHAR(64)  NOT NULL PRIMARY KEY,      -- 锁名
    lock_until TIMESTAMP(3) NOT NULL,                  -- 锁到期时刻
    locked_at  TIMESTAMP(3) NOT NULL,                  -- 加锁时刻
    locked_by  VARCHAR(255) NOT NULL                   -- 持有者实例标识
);
