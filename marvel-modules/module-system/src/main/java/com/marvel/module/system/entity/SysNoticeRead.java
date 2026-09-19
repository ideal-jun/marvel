package com.marvel.module.system.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 公告已读记录（sys_notice_read）：按 notice_id + user_id 复合主键。 */
@Data
@TableName("sys_notice_read")
public class SysNoticeRead {

    private Long noticeId;

    private Long userId;

    private LocalDateTime readTime;

    /** 0=正常 1=该用户已删除（软删除，仅影响当前用户的消息列表） */
    private Integer deleted;
}
