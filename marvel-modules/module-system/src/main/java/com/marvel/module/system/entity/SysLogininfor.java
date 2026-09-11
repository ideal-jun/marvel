package com.marvel.module.system.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 登录日志（sys_logininfor）：登录成功/失败的审计记录。
 */
@Data
@TableName("sys_logininfor")
public class SysLogininfor {

    @TableId(type = IdType.AUTO)
    private Long infoId;

    private String username;

    private String ipaddr;

    /** 浏览器类型（User-Agent 解析） */
    private String browser;

    /** 操作系统（User-Agent 解析） */
    private String os;

    /** 提示消息（成功/失败原因） */
    private String msg;

    /** 0=成功 1=失败 */
    private String status;

    private LocalDateTime loginTime;
}
