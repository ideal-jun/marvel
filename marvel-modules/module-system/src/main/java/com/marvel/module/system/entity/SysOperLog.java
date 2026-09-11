package com.marvel.module.system.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 操作日志（sys_oper_log）：@Log 注解方法的审计记录，由 OperLogAspect 发布事件落库。
 */
@Data
@TableName("sys_oper_log")
public class SysOperLog {

    @TableId(type = IdType.AUTO)
    private Long operId;

    /** 模块标题（如「用户管理」） */
    private String title;

    /** 业务类型 + 方法全名（如 "UPDATE com.marvel...SysUserController.update"） */
    private String method;

    /** HTTP 请求方式（GET/POST/...） */
    private String requestMethod;

    /** 请求 URI */
    private String operUrl;

    /** 请求参数（敏感字段已脱敏并截断） */
    private String operParam;

    /** 操作人用户 ID（未登录为 anonymous） */
    private String operUser;

    private String operIp;

    /** 0=成功 1=失败 */
    private String status;

    /** 失败时的异常消息 */
    private String errorMsg;

    private LocalDateTime operTime;
}
