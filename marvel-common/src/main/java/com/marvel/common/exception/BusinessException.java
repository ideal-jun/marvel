package com.marvel.common.exception;

import lombok.Getter;

import java.io.Serializable;

/**
 * 业务异常：表示可预期的业务规则不满足（参数非法、状态冲突、重复数据等），
 * 由全局异常处理器统一转换为 HTTP 400 + {@code R.fail(code, message)}。
 *
 * <p>错误码约定：非系统故障请使用 400，不要用 500，以便前端与网关区分「业务失败」与「系统故障」。
 */
@Getter
public class BusinessException extends RuntimeException implements Serializable {

    private static final long serialVersionUID = 1L;

    private final int code;

    public BusinessException(String message) {
        this(400, message);
    }

    public BusinessException(int code, String message) {
        super(message);
        this.code = code;
    }
}
