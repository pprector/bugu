-- E9 运营后台:后台账号与操作审计(与 App 账号 app_user 完全隔离)

-- 后台账号(E9)
CREATE TABLE admin_user
(
    id            BIGSERIAL PRIMARY KEY,
    username      VARCHAR(32)  NOT NULL,               -- 登录名
    password_hash VARCHAR(200) NOT NULL,               -- PBKDF2WithHmacSHA256,格式 pbkdf2:<iter>:<saltB64>:<hashB64>
    nickname      VARCHAR(32)  NOT NULL,
    role          VARCHAR(16)  NOT NULL DEFAULT 'OPS', -- SUPER(全量) / OPS(只读)
    enabled       BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at    TIMESTAMP(3) NOT NULL,
    CONSTRAINT uk_admin_username UNIQUE (username)
);

-- 后台操作审计(E9)
CREATE TABLE admin_audit_log
(
    id            BIGSERIAL PRIMARY KEY,
    admin_user_id BIGINT       NOT NULL,
    action        VARCHAR(64)  NOT NULL,               -- LOGIN / USER_ELDER_MODE / BOND_UNBIND / TEMPLATE_SAVE 等
    target_type   VARCHAR(32),
    target_id     VARCHAR(32),
    detail        VARCHAR(500),
    created_at    TIMESTAMP(3) NOT NULL
);
CREATE INDEX idx_audit_admin ON admin_audit_log (admin_user_id, created_at);
