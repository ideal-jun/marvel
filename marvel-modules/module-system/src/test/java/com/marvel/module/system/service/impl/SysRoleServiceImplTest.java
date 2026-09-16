package com.marvel.module.system.service.impl;

import com.marvel.module.system.entity.SysRole;
import com.marvel.module.system.mapper.SysMenuMapper;
import com.marvel.module.system.mapper.SysRoleMapper;
import com.marvel.module.system.mapper.SysRoleMenuMapper;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SysRoleServiceImplTest {

    private SysRoleServiceImpl serviceWith(SysRoleMapper roleMapper, SysMenuMapper menuMapper) {
        SysRoleServiceImpl service = new SysRoleServiceImpl(mock(SysRoleMenuMapper.class), menuMapper);
        ReflectionTestUtils.setField(service, "baseMapper", roleMapper);
        return service;
    }

    @Test
    void adminRoleGetsWildcardPermissionAsMutableHashSet() {
        SysRoleMapper roleMapper = mock(SysRoleMapper.class);
        SysRole admin = new SysRole();
        admin.setRoleId(1L);
        admin.setRoleKey("admin");
        when(roleMapper.selectRolesByUserId(1L)).thenReturn(List.of(admin));

        Set<String> perms = serviceWith(roleMapper, mock(SysMenuMapper.class)).getPermissionsByUserId(1L);

        assertThat(perms).containsExactly("*:*:*");
        // 必须是可变实现，否则 Redis 缓存序列化器无法还原
        assertThat(perms).isInstanceOf(HashSet.class);
    }

    @Test
    void normalRoleUsesMenuPermissions() {
        SysRoleMapper roleMapper = mock(SysRoleMapper.class);
        SysRole common = new SysRole();
        common.setRoleId(2L);
        common.setRoleKey("common");
        when(roleMapper.selectRolesByUserId(2L)).thenReturn(List.of(common));

        SysMenuMapper menuMapper = mock(SysMenuMapper.class);
        when(menuMapper.selectPermsByUserId(2L)).thenReturn(List.of("system:user:list"));

        Set<String> perms = serviceWith(roleMapper, menuMapper).getPermissionsByUserId(2L);

        assertThat(perms).containsExactly("system:user:list");
    }
}
