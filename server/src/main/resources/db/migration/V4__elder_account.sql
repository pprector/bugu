-- ============================================================
-- V4:长辈免注册(story 1.4)——设备标识账号与关系标签
-- app_user 加 device_uuid(设备幂等键,长辈账号唯一);
-- bond 加 relation_tag(关系标签属关系维度,子女端代填)
-- ============================================================

ALTER TABLE app_user
    ADD COLUMN device_uuid VARCHAR(64),                -- 设备标识(DeviceUUID),长辈免注册账号幂等键
    ADD CONSTRAINT uk_user_device UNIQUE (device_uuid);

ALTER TABLE bond
    ADD COLUMN relation_tag VARCHAR(16);               -- 关系标签(妈妈/爸爸等),子女端代填
