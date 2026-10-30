-- reminder_fire 增加目标用户列(epic-mutual-alarm S1):
-- 互叫闹钟到点双方各生成一条 fire,各自确认只关自己;ONCE/REPEAT 恒等于 reminder.receiver_id
ALTER TABLE reminder_fire
    ADD COLUMN target_user_id BIGINT;                  -- 本次触发的目标用户(互叫双方各一条)

UPDATE reminder_fire f
    SET target_user_id = r.receiver_id
    FROM reminder r
    WHERE f.reminder_id = r.id;

ALTER TABLE reminder_fire
    ALTER COLUMN target_user_id SET NOT NULL;

CREATE INDEX idx_fire_target ON reminder_fire (target_user_id);
