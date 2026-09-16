package com.marvel.framework.web;

import cn.dev33.satoken.exception.NotLoginException;
import cn.dev33.satoken.exception.NotPermissionException;
import cn.dev33.satoken.exception.NotRoleException;
import com.marvel.common.exception.BusinessException;
import com.marvel.common.result.R;
import com.marvel.common.result.ResultCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.validation.BindException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Slf4j
@RestControllerAdvice
/** 全局异常处理：业务异常、认证/授权异常、参数校验异常、系统异常统一转换为统一返回体，避免堆栈信息外泄。 */
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public R<Void> handleBusiness(BusinessException e) {
        log.warn("业务异常: {}", e.getMessage());
        return R.fail(e.getCode(), e.getMessage());
    }

    @ExceptionHandler(NotLoginException.class)
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    public R<Void> handleNotLogin(NotLoginException e) {
        return R.fail(ResultCode.UNAUTHORIZED.getCode(), ResultCode.UNAUTHORIZED.getMsg());
    }

    @ExceptionHandler({NotPermissionException.class, NotRoleException.class})
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public R<Void> handleNotPermission(Exception e) {
        return R.fail(ResultCode.FORBIDDEN.getCode(), ResultCode.FORBIDDEN.getMsg());
    }

    @ExceptionHandler({MethodArgumentNotValidException.class, BindException.class})
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public R<Void> handleValidation(BindException e) {
        String msg = e.getBindingResult().getFieldErrors().stream()
                .map(f -> f.getField() + " " + f.getDefaultMessage())
                .findFirst()
                .orElse("参数校验失败");
        return R.fail(ResultCode.BAD_REQUEST.getCode(), msg);
    }

    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public R<Void> handleException(Exception e) {
        log.error("系统异常", e);
        return R.fail("系统繁忙，请稍后重试");
    }
}
