-- copy_template 补足:三档 REPUSH 各补至 5 条(PRD FR-05 三档各≥5条轮换)
-- V9 已种每档 CREATE/REPUSH/WAKEUP 各 1 条,本迁移只补 REPUSH(升级链主力场景)
-- 文案约束:≤40 字、无指责性表述;GENTLE 关怀不施压 / NAG 重复缘由 / STRICT 直接陈述
INSERT INTO copy_template (tone, scene, content, enabled, created_at) VALUES
('GENTLE', 'REPUSH', '这件事还等着你呢,方便时看一眼~',   TRUE, CURRENT_TIMESTAMP(3)),
('GENTLE', 'REPUSH', '小小提醒一下,别太累着自己哦~',     TRUE, CURRENT_TIMESTAMP(3)),
('GENTLE', 'REPUSH', '到点该做这件事啦,慢慢来不着急~',   TRUE, CURRENT_TIMESTAMP(3)),
('GENTLE', 'REPUSH', '又来看看你啦,记得这件事哦~',       TRUE, CURRENT_TIMESTAMP(3)),
('NAG',    'REPUSH', '这事说过啦,怎么还没动静?快去看看!', TRUE, CURRENT_TIMESTAMP(3)),
('NAG',    'REPUSH', '又提醒一次,这事可不能再拖了!',      TRUE, CURRENT_TIMESTAMP(3)),
('NAG',    'REPUSH', '别忘了这事,做完跟我说一声!',        TRUE, CURRENT_TIMESTAMP(3)),
('NAG',    'REPUSH', '这事还挂着呢,赶紧处理一下吧!',      TRUE, CURRENT_TIMESTAMP(3)),
('STRICT', 'REPUSH', '该事项尚未完成,请立即处理。',       TRUE, CURRENT_TIMESTAMP(3)),
('STRICT', 'REPUSH', '提醒仍未确认,请现在执行。',         TRUE, CURRENT_TIMESTAMP(3)),
('STRICT', 'REPUSH', '时间已过,请立刻完成该事项。',       TRUE, CURRENT_TIMESTAMP(3)),
('STRICT', 'REPUSH', '请勿再拖延,马上处理。',             TRUE, CURRENT_TIMESTAMP(3));
