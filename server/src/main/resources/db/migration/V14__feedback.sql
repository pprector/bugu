CREATE TABLE feedback
(
    id           BIGSERIAL PRIMARY KEY,
    user_id      BIGINT       NOT NULL REFERENCES app_user(id),
    category     VARCHAR(16)  NOT NULL,
    content      VARCHAR(2000) NOT NULL,
    app_version  VARCHAR(64),
    status       VARCHAR(16)  NOT NULL DEFAULT 'OPEN',
    reply        VARCHAR(2000),
    created_at   TIMESTAMP(3) NOT NULL,
    updated_at   TIMESTAMP(3) NOT NULL,
    CONSTRAINT ck_feedback_category CHECK (category IN ('REMINDER', 'ACCOUNT', 'SUGGESTION', 'OTHER')),
    CONSTRAINT ck_feedback_status CHECK (status IN ('OPEN', 'PROCESSING', 'RESOLVED'))
);
CREATE INDEX idx_feedback_user_created ON feedback (user_id, created_at DESC, id DESC);
CREATE INDEX idx_feedback_status_created ON feedback (status, created_at DESC, id DESC);
