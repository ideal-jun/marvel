-- 公告已读记录（站内消息已读状态按用户维度存储）
CREATE TABLE sys_notice_read (
    notice_id BIGINT   NOT NULL                COMMENT '公告ID',
    user_id   BIGINT   NOT NULL                COMMENT '用户ID',
    read_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '已读时间',
    PRIMARY KEY (notice_id, user_id),
    KEY idx_notice_read_user (user_id)
) ENGINE=InnoDB COMMENT='公告已读记录';
