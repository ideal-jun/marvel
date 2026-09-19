package com.marvel.api.system.dto;

import lombok.Data;

import java.io.Serializable;

@Data
public class SysUserDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;
    private String username;
    /** BCrypt 密文，仅供认证域校验使用 */
    private String password;
    private String nickname;
    /** 头像 URL（可能为空，前端回退为昵称首字） */
    private String avatar;
    private Long deptId;
    private String deptName;
    private String email;
    private String phone;
    private String status;
}
