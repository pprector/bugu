-- escalation_log 增加 (fire_id, stage) 唯一约束:
-- FIRE_AT 到点推送以此做原子幂等 claim(INSERT 成功者才发送,重复实例插入冲突即跳过);
-- T1/T2/CLOSE 同一 fire 同一阶段本就不应重复,一并由数据库兜底。
-- 先清理历史重复行(保留最早一条),避免迁移在已有环境失败。
DELETE FROM escalation_log duplicate
USING escalation_log original
WHERE duplicate.fire_id = original.fire_id
  AND duplicate.stage = original.stage
  AND duplicate.id > original.id;

CREATE UNIQUE INDEX uk_escalation_fire_stage ON escalation_log (fire_id, stage);
