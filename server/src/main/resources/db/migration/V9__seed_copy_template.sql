-- copy_template 种子数据:三档语气 x 场景的基础降级文案(epic-ai-engine 会补充到各≥5条)(PostgreSQL 方言)
-- scene: CREATE / REPUSH / WAKEUP ; tone: GENTLE / NAG / STRICT
INSERT INTO copy_template (tone, scene, content, enabled, created_at) VALUES
('GENTLE', 'REPUSH',  '该提醒事项了,有空看一下吧~', TRUE, CURRENT_TIMESTAMP(3)),
('GENTLE', 'CREATE',  '给你设了个提醒,到点记得哦~', TRUE, CURRENT_TIMESTAMP(3)),
('GENTLE', 'WAKEUP',  '早安,该起床啦,新的一天加油~', TRUE, CURRENT_TIMESTAMP(3)),
('NAG',    'REPUSH',  '这事还没做呢,别忘了!',         TRUE, CURRENT_TIMESTAMP(3)),
('NAG',    'CREATE',  '给你提个醒,这事可得记牢!',     TRUE, CURRENT_TIMESTAMP(3)),
('NAG',    'WAKEUP',  '起床了!再不起要迟到啦!',       TRUE, CURRENT_TIMESTAMP(3)),
('STRICT', 'REPUSH',  '请立即处理该提醒事项。',         TRUE, CURRENT_TIMESTAMP(3)),
('STRICT', 'CREATE', '你有一条新提醒,请按时完成。',   TRUE, CURRENT_TIMESTAMP(3)),
('STRICT', 'WAKEUP', '立即起床。',                    TRUE, CURRENT_TIMESTAMP(3));
