-- 站内消息支持「我的消息」按用户软删除：
-- 已读记录表增加 deleted 标记，删除仅影响当前用户可见性，不删除公告本身。
ALTER TABLE sys_notice_read
    ADD COLUMN deleted TINYINT NOT NULL DEFAULT 0 COMMENT '0=正常 1=用户已删除' AFTER read_time;
