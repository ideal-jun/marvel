package com.marvel.api.system;

import com.marvel.api.system.dto.MenuDTO;
import com.marvel.api.system.dto.SysUserDTO;

import java.util.List;
import java.util.Set;

/**
 * system 域对外契约。
 * 单体期内由 module-system 在容器内实现；拆分 Spring Cloud 时由 Feign 客户端实现替换。
 */
public interface SystemApi {

    SysUserDTO getUserByUsername(String username);

    SysUserDTO getUserById(Long userId);

    /** 用户角色 key 集合（如 admin） */
    Set<String> getRoleKeysByUserId(Long userId);

    /** 用户权限标识集合（如 system:user:list） */
    Set<String> getPermissionsByUserId(Long userId);

    /** 用户可见菜单树（仅 M/C 类型，已按角色过滤） */
    List<MenuDTO> getMenusByUserId(Long userId);

    /** 读取系统参数值（键不存在返回 null） */
    String getConfigValue(String configKey);
}
