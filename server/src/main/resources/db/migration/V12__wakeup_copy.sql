-- 互叫闹钟叫醒语按日缓存(epic-mutual-alarm S3):
-- 当日首次 AI 生成并落库,同日幂等命中;降级结果也落缓存,保证当日内容稳定(端侧断网可播)
CREATE TABLE wakeup_copy
(
    id          BIGSERIAL PRIMARY KEY,                 -- 叫醒语缓存(E5)
    reminder_id BIGINT      NOT NULL,
    copy_date   DATE        NOT NULL,                  -- 生效日期
    content     VARCHAR(80) NOT NULL,                  -- ≤40 字
    degraded    BOOLEAN     NOT NULL DEFAULT FALSE,    -- 是否降级模板
    created_at  TIMESTAMP(3) NOT NULL,
    CONSTRAINT uk_wakeup_copy_day UNIQUE (reminder_id, copy_date),
    CONSTRAINT fk_wakeup_copy_reminder FOREIGN KEY (reminder_id) REFERENCES reminder (id)
);

-- WAKEUP 三档模板各补 4 条至 5 条(FR-05:三档各备至少 5 条轮换;≤40 字、无指责性表述)
INSERT INTO copy_template (tone, scene, content, enabled, created_at) VALUES
('GENTLE', 'WAKEUP', '早安呀,美好的一天从起床开始~', TRUE, CURRENT_TIMESTAMP(3)),
('GENTLE', 'WAKEUP', '太阳晒屁股啦,轻轻起床吧~',     TRUE, CURRENT_TIMESTAMP(3)),
('GENTLE', 'WAKEUP', '新的一天来了,愿你元气满满~',   TRUE, CURRENT_TIMESTAMP(3)),
('GENTLE', 'WAKEUP', '该起床啦,今天也被温柔以待~',   TRUE, CURRENT_TIMESTAMP(3)),
('NAG',    'WAKEUP', '起床啦起床啦,别再赖床喽!',     TRUE, CURRENT_TIMESTAMP(3)),
('NAG',    'WAKEUP', '闹钟响啦,快起来迎接今天吧!',   TRUE, CURRENT_TIMESTAMP(3)),
('NAG',    'WAKEUP', '再不起床,早餐都要凉啦!',       TRUE, CURRENT_TIMESTAMP(3)),
('NAG',    'WAKEUP', '快快起床,今天的事等着你呢!',   TRUE, CURRENT_TIMESTAMP(3)),
('STRICT', 'WAKEUP', '时间到,请立即起床。',           TRUE, CURRENT_TIMESTAMP(3)),
('STRICT', 'WAKEUP', '起床时间已到,请勿拖延。',       TRUE, CURRENT_TIMESTAMP(3)),
('STRICT', 'WAKEUP', '请现在起床,开始今日安排。',     TRUE, CURRENT_TIMESTAMP(3)),
('STRICT', 'WAKEUP', '闹钟已响,请按时起床。',         TRUE, CURRENT_TIMESTAMP(3));
