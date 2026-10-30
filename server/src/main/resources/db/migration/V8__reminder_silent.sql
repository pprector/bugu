-- reminder 追加 silent 列:同一对接收/发起人当日创建超 10 条后静默落库
-- (不打断创建流程,端侧灰显不横幅;PRD 防打扰软着陆,S4)
ALTER TABLE reminder
    ADD COLUMN silent BOOLEAN NOT NULL DEFAULT FALSE;  -- 超出每日上限静默标记
