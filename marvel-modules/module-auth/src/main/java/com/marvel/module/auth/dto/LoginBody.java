package com.marvel.module.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 登录请求体。字段长度在入口处限制，防止超长输入攻击。
 */
@Data
public class LoginBody {

    @NotBlank(message = "用户名不能为空")
    @Size(min = 2, max = 30, message = "用户名长度需在 2-30 位之间")
    private String username;

    @NotBlank(message = "密码不能为空")
    @Size(min = 6, max = 32, message = "密码长度需在 6-32 位之间")
    private String password;

    /** 验证码（系统参数 sys.captcha.enabled=false 时可为空） */
    @Size(max = 10, message = "验证码格式不正确")
    private String code;

    /** 验证码标识（系统参数 sys.captcha.enabled=false 时可为空） */
    @Size(max = 64, message = "验证码标识格式不正确")
    private String uuid;
}
