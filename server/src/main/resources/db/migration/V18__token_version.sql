-- JWT 吊销(E9):token 内携带 ver,与服务端 token_version 不一致即失效。
-- 旧 token 无 ver 视为 0,与列默认值一致,升级后存量会话不受影响。
ALTER TABLE app_user ADD COLUMN token_version INTEGER NOT NULL DEFAULT 0;
ALTER TABLE admin_user ADD COLUMN token_version INTEGER NOT NULL DEFAULT 0;
