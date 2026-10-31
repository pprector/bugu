-- 自惦记(送给自己):reminder.bond_id 允许为空,创建时跳过互伴绑定校验
ALTER TABLE reminder ALTER COLUMN bond_id DROP NOT NULL;
