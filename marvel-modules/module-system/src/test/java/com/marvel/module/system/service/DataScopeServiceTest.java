package com.marvel.module.system.service;

import com.marvel.module.system.entity.SysDept;
import com.marvel.module.system.entity.SysRole;
import com.marvel.module.system.entity.SysUser;
import com.marvel.module.system.mapper.SysDeptMapper;
import com.marvel.module.system.mapper.SysRoleDeptMapper;
import com.marvel.module.system.mapper.SysUserMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DataScopeServiceTest {

    private SysRoleService roleService;
    private SysRoleDeptMapper roleDeptMapper;
    private SysDeptMapper deptMapper;
    private SysUserMapper userMapper;
    private DataScopeService service;

    @BeforeEach
    void setUp() {
        roleService = mock(SysRoleService.class);
        roleDeptMapper = mock(SysRoleDeptMapper.class);
        deptMapper = mock(SysDeptMapper.class);
        userMapper = mock(SysUserMapper.class);
        service = new DataScopeService(roleService, roleDeptMapper, deptMapper, userMapper);
    }

    private SysRole role(long id, String key, String scope) {
        SysRole r = new SysRole();
        r.setRoleId(id);
        r.setRoleKey(key);
        r.setDataScope(scope);
        return r;
    }

    private SysUser user(Long deptId) {
        SysUser u = new SysUser();
        u.setUserId(5L);
        u.setDeptId(deptId);
        return u;
    }

    private SysDept dept(long id, String ancestors) {
        SysDept d = new SysDept();
        d.setDeptId(id);
        d.setAncestors(ancestors);
        return d;
    }

    @Test
    void adminRoleSeesAll() {
        when(roleService.listRolesByUserId(5L)).thenReturn(List.of(role(1, "admin", "4")));
        assertThat(service.resolve(5L).all()).isTrue();
    }

    @Test
    void scope1SeesAll() {
        when(roleService.listRolesByUserId(5L)).thenReturn(List.of(role(2, "common", "1")));
        assertThat(service.resolve(5L).all()).isTrue();
    }

    @Test
    void scope3SeesOwnDeptOnly() {
        when(roleService.listRolesByUserId(5L)).thenReturn(List.of(role(2, "common", "3")));
        when(userMapper.selectById(5L)).thenReturn(user(103L));

        DataScope scope = service.resolve(5L);

        assertThat(scope.all()).isFalse();
        assertThat(scope.selfUserId()).isNull();
        assertThat(scope.deptIds()).containsExactly(103L);
    }

    @Test
    void scope4SeesOwnDeptAndDescendants() {
        when(roleService.listRolesByUserId(5L)).thenReturn(List.of(role(2, "common", "4")));
        when(userMapper.selectById(5L)).thenReturn(user(103L));
        when(deptMapper.selectList(any())).thenReturn(List.of(
                dept(103, "0,100,101"), dept(105, "0,100,101,103"), dept(106, "0,100,101")));

        DataScope scope = service.resolve(5L);

        assertThat(scope.deptIds()).containsExactlyInAnyOrder(103L, 105L);
    }

    @Test
    void scope2UsesCustomRoleDept() {
        when(roleService.listRolesByUserId(5L)).thenReturn(List.of(role(7, "custom", "2")));
        when(roleDeptMapper.selectDeptIdsByRoleId(7L)).thenReturn(List.of(100L, 101L));

        DataScope scope = service.resolve(5L);

        assertThat(scope.deptIds()).containsExactlyInAnyOrder(100L, 101L);
    }

    @Test
    void scope5IsSelfOnly() {
        when(roleService.listRolesByUserId(5L)).thenReturn(List.of(role(2, "common", "5")));
        when(userMapper.selectById(5L)).thenReturn(user(103L));

        DataScope scope = service.resolve(5L);

        assertThat(scope.all()).isFalse();
        assertThat(scope.selfUserId()).isEqualTo(5L);
    }

    @Test
    void unknownScopeFallsBackToOwnDept() {
        when(roleService.listRolesByUserId(5L)).thenReturn(List.of(role(2, "common", null)));
        when(userMapper.selectById(5L)).thenReturn(user(103L));

        DataScope scope = service.resolve(5L);

        assertThat(scope.all()).isFalse();
        assertThat(scope.deptIds()).containsExactly(103L);
    }

    @Test
    void noRolesSeesNothing() {
        when(roleService.listRolesByUserId(5L)).thenReturn(List.of());

        DataScope scope = service.resolve(5L);

        assertThat(scope.all()).isFalse();
        assertThat(scope.selfUserId()).isNull();
        assertThat(scope.deptIds()).isEmpty();
    }
}
