-- ============================================================================
-- 布谷(bugu)数据库 Schema 全量汇总(合并版)
-- ============================================================================
-- 来源: server/src/main/resources/db/migration/V1 ~ V15(V6 空缺,历史未使用)
-- 用途: 阅读与参考。本文件是所有迁移合并后的"最终形态",
--       不是 Flyway 迁移脚本,请勿放入 db/migration 目录。
-- 方言: PostgreSQL 16(生产 docker 容器 bugu-postgres,库名 bugu/bugu/bugu)
-- 约定: 时间统一 TIMESTAMP(3),由应用侧写入;主键统一 BIGSERIAL
--
-- 迁移历史一览:
--   V1  baseline                基线 9 表(app_user/device_token/bond/reminder/
--                               reminder_fire/reminder_ack/escalation_log/
--                               ai_gen_log/copy_template/shedlock)
--   V2  identity_sms            手机号登录:sms_code 表,app_user 加 phone/account_type
--   V3  bond_bind_code          绑定码发起:bond.receiver_id 可空、状态收敛、bind_code
--   V4  elder_account           长辈免注册:app_user.device_uuid、bond.relation_tag
--   V5  bond_confirm_throttle   绑定码确认防爆破:bond_confirm_throttle 表
--   V6  (空缺,无此迁移)
--   V7  receiver_mute_setting   接收人静音设置表
--   V8  reminder_silent         reminder 加 silent 列(当日超 10 条静默落库)
--   V9  seed_copy_template      copy_template 种子:三档语气 x 三场景各 1 条(9 条)
--   V10 seed_copy_template_repush  REPUSH 场景三档各补至 5 条(12 条)
--   V11 reminder_fire_target_user   reminder_fire 加 target_user_id(互叫双方各一条 fire)
--   V12 wakeup_copy             叫醒语按日缓存表 + WAKEUP 模板三档各补至 5 条(12 条)
--   V13 admin_rbac              后台账号与审计:admin_user / admin_audit_log
--   V14 feedback                用户反馈表
--   V15 reminder_self           自惦记:reminder.bond_id 改可空
-- ============================================================================


-- ============================================================================
-- 一、用户与身份
-- ============================================================================

-- 用户(V1 基线 + V2 手机号 + V4 长辈设备账号)
CREATE TABLE app_user
(
    id            BIGSERIAL   PRIMARY KEY,
    nickname      VARCHAR(32) NOT NULL,                  -- 昵称,长辈端大字展示
    avatar_url    VARCHAR(512),                          -- 头像地址(现为腾讯云 COS 公有读地址)
    elder_mode    BOOLEAN     NOT NULL DEFAULT FALSE,    -- 长辈极简模式(FR-06)
    account_state VARCHAR(16) NOT NULL DEFAULT 'ACTIVE', -- ACTIVE / DISABLED
    phone         VARCHAR(20),                           -- 大陆手机号,登录标识;长辈设备账号为 NULL(V2)
    account_type  VARCHAR(16) NOT NULL DEFAULT 'NORMAL', -- NORMAL / ELDER(V2)
    device_uuid   VARCHAR(64),                           -- 设备标识,长辈免注册账号幂等键(V4)
    created_at    TIMESTAMP(3) NOT NULL,
    updated_at    TIMESTAMP(3) NOT NULL,
    CONSTRAINT uk_user_phone  UNIQUE (phone),             -- V2
    CONSTRAINT uk_user_device UNIQUE (device_uuid)        -- V4
);

-- 短信验证码与发送限流(V2,按手机号一行,DB 计数不用 Redis)
CREATE TABLE sms_code
(
    phone        VARCHAR(20)  NOT NULL PRIMARY KEY,      -- 手机号(一行一手机)
    code_hash    CHAR(64)     NOT NULL,                  -- SHA-256(code),不落明文
    send_count   INT          NOT NULL DEFAULT 1,        -- 当前 60s 窗口内已发次数
    window_start TIMESTAMP(3) NOT NULL,                  -- 当前计数窗口起点
    locked_until TIMESTAMP(3),                           -- 限流锁到期时刻(60s 内第 6 次触发,锁 15min)
    sent_at      TIMESTAMP(3) NOT NULL,                  -- 最近一次发码时刻(验证码有效期起点)
    created_at   TIMESTAMP(3) NOT NULL,
    updated_at   TIMESTAMP(3) NOT NULL
);

-- 后台账号(V13,与 App 账号 app_user 完全隔离)
CREATE TABLE admin_user
(
    id            BIGSERIAL PRIMARY KEY,
    username      VARCHAR(32)  NOT NULL,                  -- 登录名
    password_hash VARCHAR(200) NOT NULL,                  -- PBKDF2WithHmacSHA256,格式 pbkdf2:<iter>:<saltB64>:<hashB64>
    nickname      VARCHAR(32)  NOT NULL,
    role          VARCHAR(16)  NOT NULL DEFAULT 'OPS',    -- SUPER(全量) / OPS(只读)
    enabled       BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at    TIMESTAMP(3) NOT NULL,
    CONSTRAINT uk_admin_username UNIQUE (username)
);

-- 后台操作审计(V13)
CREATE TABLE admin_audit_log
(
    id            BIGSERIAL PRIMARY KEY,
    admin_user_id BIGINT       NOT NULL,
    action        VARCHAR(64)  NOT NULL,                  -- LOGIN / USER_ELDER_MODE / BOND_UNBIND / TEMPLATE_SAVE 等
    target_type   VARCHAR(32),
    target_id     VARCHAR(32),
    detail        VARCHAR(500),
    created_at    TIMESTAMP(3) NOT NULL
);
CREATE INDEX idx_audit_admin ON admin_audit_log (admin_user_id, created_at);


-- ============================================================================
-- 二、设备与绑定关系
-- ============================================================================

-- 设备与推送令牌(V1)
CREATE TABLE device_token
(
    id             BIGSERIAL PRIMARY KEY,
    user_id        BIGINT       NOT NULL,
    platform       VARCHAR(16)  NOT NULL,                 -- ANDROID / IOS / HARMONY
    vendor         VARCHAR(32),                           -- HUAWEI / XIAOMI / OPPO / VIVO / HONOR / APNS / HARMONY_PUSH
    push_token     VARCHAR(512) NOT NULL,
    push_enabled   BOOLEAN      NOT NULL DEFAULT TRUE,
    last_active_at TIMESTAMP(3),
    created_at     TIMESTAMP(3) NOT NULL,
    updated_at     TIMESTAMP(3) NOT NULL,
    CONSTRAINT uk_push_token UNIQUE (push_token),
    CONSTRAINT fk_device_user FOREIGN KEY (user_id) REFERENCES app_user (id)
);
CREATE INDEX idx_device_user ON device_token (user_id);

-- 绑定关系 1v1(V1 基线 + V3 绑定码 + V4 关系标签)
CREATE TABLE bond
(
    id             BIGSERIAL PRIMARY KEY,
    requester_id   BIGINT       NOT NULL,                 -- 发起人
    receiver_id    BIGINT,                                -- 接收人;PENDING_OUTBOUND 期间为 NULL,确认时回填(V3)
    status         VARCHAR(16)  NOT NULL DEFAULT 'PENDING_OUTBOUND', -- PENDING_OUTBOUND / ACTIVE / UNBOUND(V3 收敛)
    bind_code      CHAR(6),                               -- 绑定码,PENDING_OUTBOUND 期间唯一,确认后清空(V3)
    code_expires_at TIMESTAMP(3),                         -- 绑定码过期时刻(10 分钟)(V3)
    relation_tag   VARCHAR(16),                           -- 关系标签(妈妈/爸爸等),子女端代填(V4)
    confirmed_at   TIMESTAMP(3),                          -- 双向同意时刻
    unbound_at     TIMESTAMP(3),                          -- 解绑时刻,解绑即时生效
    created_at     TIMESTAMP(3) NOT NULL,
    updated_at     TIMESTAMP(3) NOT NULL,
    CONSTRAINT uk_bond_bind_code UNIQUE (bind_code),
    CONSTRAINT fk_bond_requester FOREIGN KEY (requester_id) REFERENCES app_user (id),
    CONSTRAINT fk_bond_receiver  FOREIGN KEY (receiver_id)  REFERENCES app_user (id)
);
CREATE INDEX idx_bond_requester ON bond (requester_id);
CREATE INDEX idx_bond_receiver  ON bond (receiver_id);

-- 绑定码确认失败限流(V5,按确认方用户一行,DB 计数)
CREATE TABLE bond_confirm_throttle
(
    user_id      BIGINT       NOT NULL PRIMARY KEY,        -- 确认方用户 id(一行一用户)
    fail_count   INT          NOT NULL DEFAULT 0,          -- 当前窗口内确认失败次数
    window_start TIMESTAMP(3) NOT NULL,                   -- 当前计数窗口起点
    locked_until TIMESTAMP(3),                            -- 限流锁到期时刻(窗口内失败超限触发)
    created_at   TIMESTAMP(3) NOT NULL,
    updated_at   TIMESTAMP(3) NOT NULL
);

-- 接收人静音设置(V7,存在即静音,删除即恢复)
-- 维度=接收人×发起人×类型(静音意图针对人而非关系实例,解绑再绑延续)
CREATE TABLE receiver_mute_setting
(
    id          BIGSERIAL PRIMARY KEY,
    receiver_id BIGINT      NOT NULL,                    -- 接收人(设置者)
    creator_id  BIGINT      NOT NULL,                    -- 被静音的发起人
    kind        VARCHAR(16) NOT NULL,                    -- ONCE / REPEAT / MUTUAL_ALARM
    created_at  TIMESTAMP(3) NOT NULL,
    CONSTRAINT uk_mute UNIQUE (receiver_id, creator_id, kind),
    CONSTRAINT fk_mute_receiver FOREIGN KEY (receiver_id) REFERENCES app_user (id),
    CONSTRAINT fk_mute_creator  FOREIGN KEY (creator_id)  REFERENCES app_user (id)
);


-- ============================================================================
-- 三、惦记(提醒)核心链路
-- ============================================================================

-- 提醒定义(V1 基线 + V8 静默标记 + V15 自惦记)
CREATE TABLE reminder
(
    id            BIGSERIAL PRIMARY KEY,
    bond_id       BIGINT,                                -- 绑定关系;自惦记(送给自己)时为 NULL(V15)
    creator_id    BIGINT       NOT NULL,                -- 发起人
    receiver_id   BIGINT       NOT NULL,                -- 接收人
    title         VARCHAR(40)  NOT NULL,                -- PRD 约束:≤20 字
    kind          VARCHAR(16)  NOT NULL,                -- ONCE / REPEAT / MUTUAL_ALARM
    tone          VARCHAR(16)  NOT NULL DEFAULT 'GENTLE', -- GENTLE / NAG / STRICT
    fire_at       TIMESTAMP(3) NOT NULL,               -- 计划触发时刻
    repeat_rule   VARCHAR(64),                         -- 重复规则
    voice_enabled BOOLEAN      NOT NULL DEFAULT FALSE, -- 是否语音播报
    note          VARCHAR(200),                        -- 备注,仅端侧展示,不上云做 AI 输入
    silent        BOOLEAN      NOT NULL DEFAULT FALSE, -- 同一对接收/发起人当日创建超 10 条后静默落库(V8)
    status        VARCHAR(16)  NOT NULL DEFAULT 'PENDING', -- PENDING / FIRED / ACKED / SNOOZED / DECLINED / MISSED
    created_at    TIMESTAMP(3) NOT NULL,
    updated_at    TIMESTAMP(3) NOT NULL,
    CONSTRAINT fk_reminder_bond     FOREIGN KEY (bond_id)     REFERENCES bond (id),
    CONSTRAINT fk_reminder_creator  FOREIGN KEY (creator_id)  REFERENCES app_user (id),
    CONSTRAINT fk_reminder_receiver FOREIGN KEY (receiver_id) REFERENCES app_user (id)
);
CREATE INDEX idx_reminder_receiver ON reminder (receiver_id, status);
CREATE INDEX idx_reminder_bond     ON reminder (bond_id);
CREATE INDEX idx_reminder_fire_at  ON reminder (fire_at);

-- 触发记录(V1 基线 + V11 目标用户;一次触发,升级链唯一事实源)
CREATE TABLE reminder_fire
(
    id              BIGSERIAL PRIMARY KEY,
    reminder_id     BIGINT       NOT NULL,
    fire_at         TIMESTAMP(3) NOT NULL,              -- 本次计划触发时刻
    reported_at     TIMESTAMP(3),                       -- 端侧上报时刻,断网时恢复后补报
    source          VARCHAR(16),                        -- LOCAL_ALARM / PUSH
    state           VARCHAR(16)  NOT NULL DEFAULT 'PENDING', -- PENDING / RESPONDED / CLOSED
    target_user_id  BIGINT       NOT NULL,              -- 本次触发的目标用户(互叫闹钟双方各一条,各自确认只关自己;ONCE/REPEAT=receiver_id)(V11)
    idempotency_key VARCHAR(96)  NOT NULL,              -- reminderId:fireAt,端侧上报幂等键
    created_at      TIMESTAMP(3) NOT NULL,
    CONSTRAINT uk_fire_idem UNIQUE (idempotency_key),
    CONSTRAINT fk_fire_reminder FOREIGN KEY (reminder_id) REFERENCES reminder (id)
);
CREATE INDEX idx_fire_state  ON reminder_fire (state, fire_at);
CREATE INDEX idx_fire_reminder ON reminder_fire (reminder_id);
CREATE INDEX idx_fire_target ON reminder_fire (target_user_id);   -- V11

-- 响应记录(V1,FR-03 三出口)
CREATE TABLE reminder_ack
(
    id             BIGSERIAL PRIMARY KEY,
    fire_id        BIGINT       NOT NULL,
    action         VARCHAR(16)  NOT NULL,               -- DONE / SNOOZE / DECLINE
    reason_code    VARCHAR(16),                         -- BUSY / CANNOT / FORGOT / OTHER
    snooze_minutes INT,                                 -- 稍后提醒间隔
    acked_at       TIMESTAMP(3) NOT NULL,
    CONSTRAINT uk_ack_fire UNIQUE (fire_id),
    CONSTRAINT fk_ack_fire FOREIGN KEY (fire_id) REFERENCES reminder_fire (id)
);

-- 升级链日志(V1,T1=15min 重推 / T2 通知创建者 / 24h 收口)
CREATE TABLE escalation_log
(
    id          BIGSERIAL PRIMARY KEY,
    fire_id     BIGINT      NOT NULL,
    stage       VARCHAR(16) NOT NULL,                    -- T1 / T2 / CLOSE
    action      VARCHAR(32) NOT NULL,                    -- REPUSH_RECEIVER / NOTIFY_CREATOR / MARK_MISSED
    push_result VARCHAR(32),                             -- ACCEPTED / FAILED / SKIPPED
    created_at  TIMESTAMP(3) NOT NULL,
    CONSTRAINT fk_escalation_fire FOREIGN KEY (fire_id) REFERENCES reminder_fire (id)
);
CREATE INDEX idx_escalation_fire ON escalation_log (fire_id);

-- 互叫闹钟叫醒语按日缓存(V12,当日首次 AI 生成落库,同日幂等;降级结果也缓存,端侧断网可播)
CREATE TABLE wakeup_copy
(
    id          BIGSERIAL PRIMARY KEY,
    reminder_id BIGINT      NOT NULL,
    copy_date   DATE        NOT NULL,                    -- 生效日期
    content     VARCHAR(80) NOT NULL,                    -- ≤40 字
    degraded    BOOLEAN     NOT NULL DEFAULT FALSE,      -- 是否降级模板
    created_at  TIMESTAMP(3) NOT NULL,
    CONSTRAINT uk_wakeup_copy_day UNIQUE (reminder_id, copy_date),
    CONSTRAINT fk_wakeup_copy_reminder FOREIGN KEY (reminder_id) REFERENCES reminder (id)
);

-- 用户反馈(V14)
CREATE TABLE feedback
(
    id          BIGSERIAL PRIMARY KEY,
    user_id     BIGINT        NOT NULL REFERENCES app_user(id),
    category    VARCHAR(16)   NOT NULL,                 -- REMINDER / ACCOUNT / SUGGESTION / OTHER
    content     VARCHAR(2000) NOT NULL,
    app_version VARCHAR(64),
    status      VARCHAR(16)   NOT NULL DEFAULT 'OPEN',  -- OPEN / PROCESSING / RESOLVED
    reply       VARCHAR(2000),
    created_at  TIMESTAMP(3) NOT NULL,
    updated_at  TIMESTAMP(3) NOT NULL,
    CONSTRAINT ck_feedback_category CHECK (category IN ('REMINDER', 'ACCOUNT', 'SUGGESTION', 'OTHER')),
    CONSTRAINT ck_feedback_status   CHECK (status IN ('OPEN', 'PROCESSING', 'RESOLVED'))
);
CREATE INDEX idx_feedback_user_created   ON feedback (user_id, created_at DESC, id DESC);
CREATE INDEX idx_feedback_status_created ON feedback (status, created_at DESC, id DESC);


-- ============================================================================
-- 四、AI 与运营支撑
-- ============================================================================

-- AI 生成日志,成本与降级观测(V1,FR-08)
CREATE TABLE ai_gen_log
(
    id            BIGSERIAL PRIMARY KEY,
    scene         VARCHAR(32) NOT NULL,                 -- PARSE / COPY / WAKEUP
    tone          VARCHAR(16),
    degraded      BOOLEAN     NOT NULL DEFAULT FALSE,   -- 是否落入降级模板库
    latency_ms    INT,
    prompt_tokens INT,
    output_tokens INT,
    created_at    TIMESTAMP(3) NOT NULL
);
CREATE INDEX idx_ai_scene_created ON ai_gen_log (scene, created_at);

-- 降级文案模板库(V1,FR-05;种子见文末)
CREATE TABLE copy_template
(
    id         BIGSERIAL PRIMARY KEY,
    tone       VARCHAR(16) NOT NULL,                    -- GENTLE / NAG / STRICT
    scene      VARCHAR(32) NOT NULL,                    -- CREATE / REPUSH / WAKEUP
    content    VARCHAR(80) NOT NULL,                    -- ≤40 字
    enabled    BOOLEAN     NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP(3) NOT NULL
);
CREATE INDEX idx_template_pick ON copy_template (tone, scene, enabled);

-- ShedLock 分布式锁表(V1,多副本调度防重)
CREATE TABLE shedlock
(
    name       VARCHAR(64)  NOT NULL PRIMARY KEY,       -- 锁名
    lock_until TIMESTAMP(3) NOT NULL,                   -- 锁到期时刻
    locked_at  TIMESTAMP(3) NOT NULL,                   -- 加锁时刻
    locked_by  VARCHAR(255) NOT NULL                    -- 持有者实例标识
);


-- ============================================================================
-- 五、种子数据:copy_template(V9 + V10 + V12,合计 33 条)
-- 文案约束:≤40 字、无指责性表述;GENTLE 关怀不施压 / NAG 重复缘由 / STRICT 直接陈述
-- ============================================================================

-- V9:三档语气 x 三场景基础各 1 条
INSERT INTO copy_template (tone, scene, content, enabled, created_at) VALUES
('GENTLE', 'CREATE',  '给你设了个提醒,到点记得哦~',        TRUE, CURRENT_TIMESTAMP(3)),
('GENTLE', 'REPUSH',  '该提醒事项了,有空看一下吧~',        TRUE, CURRENT_TIMESTAMP(3)),
('GENTLE', 'WAKEUP',  '早安,该起床啦,新的一天加油~',      TRUE, CURRENT_TIMESTAMP(3)),
('NAG',    'CREATE',  '给你提个醒,这事可得记牢!',          TRUE, CURRENT_TIMESTAMP(3)),
('NAG',    'REPUSH',  '这事还没做呢,别忘了!',              TRUE, CURRENT_TIMESTAMP(3)),
('NAG',    'WAKEUP',  '起床了!再不起要迟到啦!',            TRUE, CURRENT_TIMESTAMP(3)),
('STRICT', 'CREATE',  '你有一条新提醒,请按时完成。',        TRUE, CURRENT_TIMESTAMP(3)),
('STRICT', 'REPUSH',  '请立即处理该提醒事项。',             TRUE, CURRENT_TIMESTAMP(3)),
('STRICT', 'WAKEUP',  '立即起床。',                         TRUE, CURRENT_TIMESTAMP(3));

-- V10:REPUSH 三档各补 4 条至 5 条(升级链主力场景)
INSERT INTO copy_template (tone, scene, content, enabled, created_at) VALUES
('GENTLE', 'REPUSH', '这件事还等着你呢,方便时看一眼~',    TRUE, CURRENT_TIMESTAMP(3)),
('GENTLE', 'REPUSH', '小小提醒一下,别太累着自己哦~',      TRUE, CURRENT_TIMESTAMP(3)),
('GENTLE', 'REPUSH', '到点该做这件事啦,慢慢来不着急~',    TRUE, CURRENT_TIMESTAMP(3)),
('GENTLE', 'REPUSH', '又来看看你啦,记得这件事哦~',        TRUE, CURRENT_TIMESTAMP(3)),
('NAG',    'REPUSH', '这事说过啦,怎么还没动静?快去看看!', TRUE, CURRENT_TIMESTAMP(3)),
('NAG',    'REPUSH', '又提醒一次,这事可不能再拖了!',      TRUE, CURRENT_TIMESTAMP(3)),
('NAG',    'REPUSH', '别忘了这事,做完跟我说一声!',        TRUE, CURRENT_TIMESTAMP(3)),
('NAG',    'REPUSH', '这事还挂着呢,赶紧处理一下吧!',      TRUE, CURRENT_TIMESTAMP(3)),
('STRICT', 'REPUSH', '该事项尚未完成,请立即处理。',       TRUE, CURRENT_TIMESTAMP(3)),
('STRICT', 'REPUSH', '提醒仍未确认,请现在执行。',         TRUE, CURRENT_TIMESTAMP(3)),
('STRICT', 'REPUSH', '时间已过,请立刻完成该事项。',       TRUE, CURRENT_TIMESTAMP(3)),
('STRICT', 'REPUSH', '请勿再拖延,马上处理。',             TRUE, CURRENT_TIMESTAMP(3));

-- V12:WAKEUP 三档各补 4 条至 5 条
INSERT INTO copy_template (tone, scene, content, enabled, created_at) VALUES
('GENTLE', 'WAKEUP', '早安呀,美好的一天从起床开始~',      TRUE, CURRENT_TIMESTAMP(3)),
('GENTLE', 'WAKEUP', '太阳晒屁股啦,轻轻起床吧~',          TRUE, CURRENT_TIMESTAMP(3)),
('GENTLE', 'WAKEUP', '新的一天来了,愿你元气满满~',        TRUE, CURRENT_TIMESTAMP(3)),
('GENTLE', 'WAKEUP', '该起床啦,今天也被温柔以待~',        TRUE, CURRENT_TIMESTAMP(3)),
('NAG',    'WAKEUP', '起床啦起床啦,别再赖床喽!',          TRUE, CURRENT_TIMESTAMP(3)),
('NAG',    'WAKEUP', '闹钟响啦,快起来迎接今天吧!',        TRUE, CURRENT_TIMESTAMP(3)),
('NAG',    'WAKEUP', '再不起床,早餐都要凉啦!',            TRUE, CURRENT_TIMESTAMP(3)),
('NAG',    'WAKEUP', '快快起床,今天的事等着你呢!',        TRUE, CURRENT_TIMESTAMP(3)),
('STRICT', 'WAKEUP', '时间到,请立即起床。',               TRUE, CURRENT_TIMESTAMP(3)),
('STRICT', 'WAKEUP', '起床时间已到,请勿拖延。',           TRUE, CURRENT_TIMESTAMP(3)),
('STRICT', 'WAKEUP', '请现在起床,开始今日安排。',         TRUE, CURRENT_TIMESTAMP(3)),
('STRICT', 'WAKEUP', '闹钟已响,请按时起床。',             TRUE, CURRENT_TIMESTAMP(3));
