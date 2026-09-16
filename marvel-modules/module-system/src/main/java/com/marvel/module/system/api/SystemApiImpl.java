package com.marvel.module.system.api;

import com.marvel.api.system.SystemApi;
import com.marvel.api.system.dto.MenuDTO;
import com.marvel.api.system.dto.SysUserDTO;
import com.marvel.module.system.entity.SysConfig;
import com.marvel.module.system.entity.SysDept;
import com.marvel.module.system.entity.SysUser;
import com.marvel.module.system.service.SysConfigService;
import com.marvel.module.system.service.SysDeptService;
import com.marvel.module.system.service.SysMenuService;
import com.marvel.module.system.service.SysRoleService;
import com.marvel.module.system.service.SysUserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;

/**
 * SystemApi 的单体实现：直接进程内调用各 Service。
 * 拆分 Spring Cloud 时，此实现替换为 Feign 客户端指向 system 服务。
 */
@Component
@RequiredArgsConstructor
public class SystemApiImpl implements SystemApi {

    private final SysUserService userService;
    private final SysRoleService roleService;
    private final SysMenuService menuService;
    private final SysDeptService deptService;
    private final SysConfigService configService;

    @Override
    public SysUserDTO getUserByUsername(String username) {
        SysUser user = userService.getOne(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<SysUser>()
                        .eq(SysUser::getUsername, username));
        return toDTO(user);
    }

    @Override
    public SysUserDTO getUserById(Long userId) {
        return toDTO(userService.getById(userId));
    }

    @Override
    public Set<String> getRoleKeysByUserId(Long userId) {
        return roleService.getRoleKeysByUserId(userId);
    }

    @Override
    public Set<String> getPermissionsByUserId(Long userId) {
        return roleService.getPermissionsByUserId(userId);
    }

    @Override
    public List<MenuDTO> getMenusByUserId(Long userId) {
        boolean admin = roleService.getRoleKeysByUserId(userId).contains("admin");
        return menuService.getMenuTree(userId, admin);
    }

    @Override
    public String getConfigValue(String configKey) {
        SysConfig config = configService.getByKey(configKey);
        return config == null ? null : config.getConfigValue();
    }

    private SysUserDTO toDTO(SysUser user) {
        if (user == null) {
            return null;
        }
        SysUserDTO dto = new SysUserDTO();
        dto.setId(user.getUserId());
        dto.setUsername(user.getUsername());
        dto.setPassword(user.getPassword());
        dto.setNickname(user.getNickname());
        dto.setDeptId(user.getDeptId());
        dto.setEmail(user.getEmail());
        dto.setPhone(user.getPhone());
        dto.setStatus(user.getStatus());
        SysDept dept = deptService.getById(user.getDeptId());
        dto.setDeptName(dept != null ? dept.getDeptName() : null);
        return dto;
    }
}
