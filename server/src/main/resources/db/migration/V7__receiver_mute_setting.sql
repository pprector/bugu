-- 接收人静音设置:存在即静音,删除即恢复(PRD FR-02 异常分支「TA 关闭了这类提醒」)
-- 维度=接收人×发起人×类型(epic Decision:静音意图针对人而非关系实例,解绑再绑延续)
CREATE TABLE receiver_mute_setting
(
    id          BIGSERIAL PRIMARY KEY,
    receiver_id BIGINT      NOT NULL,                  -- 接收人(设置者)
    creator_id  BIGINT      NOT NULL,                  -- 被静音的发起人
    kind        VARCHAR(16) NOT NULL,                  -- ONCE / REPEAT / MUTUAL_ALARM
    created_at  TIMESTAMP(3) NOT NULL,
    CONSTRAINT uk_mute UNIQUE (receiver_id, creator_id, kind),
    CONSTRAINT fk_mute_receiver FOREIGN KEY (receiver_id) REFERENCES app_user (id),
    CONSTRAINT fk_mute_creator FOREIGN KEY (creator_id) REFERENCES app_user (id)
);
