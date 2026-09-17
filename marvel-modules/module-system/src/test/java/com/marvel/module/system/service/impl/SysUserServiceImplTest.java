package com.marvel.module.system.service.impl;

import cn.dev33.satoken.stp.StpLogic;
import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.marvel.common.exception.BusinessException;
import com.marvel.module.system.entity.SysRole;
import com.marvel.module.system.entity.SysUser;
import com.marvel.module.system.entity.SysUserRole;
import com.marvel.module.system.mapper.SysUserRoleMapper;
import com.marvel.module.system.service.DataScope;
import com.marvel.module.system.service.DataScopeService;
import com.marvel.module.system.service.SysRoleService;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SysUserServiceImplTest {

    private static final String OLD_PASSWORD = "OldPass12";
    private static final String OLD_PASSWORD_HASH = new BCryptPasswordEncoder().encode(OLD_PASSWORD);

    private SysUserRoleMapper userRoleMapper;
    private SysRoleService roleService;
    private DataScopeService dataScopeService;
    private StpLogic stpLogic;
    private StpLogic originalStpLogic;
    private SysUserServiceImpl service;

    @BeforeEach
    void setUp() {
        userRoleMapper = mock(SysUserRoleMapper.class);
        roleService = mock(SysRoleService.class);
        dataScopeService = mock(DataScopeService.class);
        when(dataScopeService.current()).thenReturn(DataScope.allData());
        // 生产代码直接构造 LambdaQueryWrapper，需先注册实体元数据（无容器时 lambda 缓存为空）
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, SysUser.class);
        TableInfoHelper.initTableInfo(assistant, SysUserRole.class);
        stpLogic = mock(StpLogic.class);
        when(stpLogic.getLoginType()).thenReturn("login");
        originalStpLogic = StpUtil.stpLogic;
        StpUtil.setStpLogic(stpLogic);

        // spy 掉 MyBatis-Plus 基类 CRUD，聚焦本类业务规则（密码/越权/踢下线）
        service = org.mockito.Mockito.spy(new SysUserServiceImpl(userRoleMapper, roleService, dataScopeService));
    }

    @AfterEach
    void tearDown() {
        StpUtil.setStpLogic(originalStpLogic);
    }

    /** 设定当前操作者：admin 角色视为超级管理员 */
    private void currentActor(long userId, boolean superAdmin) {
        when(stpLogic.getLoginIdAsLong()).thenReturn(userId);
        when(roleService.getRoleKeysByUserId(userId))
                .thenReturn(superAdmin ? Set.of("admin") : Set.of("common"));
    }

    private void stubGetById(long id, SysUser db) {
        doReturn(db).when(service).getById(id);
    }

    @Test
    void pageUsersMasksPasswords() {
        SysUser u = new SysUser();
        u.setUserId(5L);
        u.setPassword("should-be-masked");
        Page<SysUser> page = new Page<>(1, 10);
        page.setRecords(new ArrayList<>(List.of(u)));
        doReturn(page).when(service).page(any(), any());

        service.pageUsers(1, 10, null, null, null, null);

        assertThat(u.getPassword()).isNull();
    }

    @Test
    void createUserEncodesPasswordAndSavesRoles() {
        currentActor(1L, true);
        doReturn(null).when(service).getOne(any());
        doAnswer(inv -> {
            inv.getArgument(0, SysUser.class).setUserId(99L);
            return true;
        }).when(service).save(any(SysUser.class));

        SysUser user = new SysUser();
        user.setUsername("neo");
        user.setPassword("Abcdef12");
        user.setStatus("0");
        service.createUser(user, List.of(2L, 3L));

        assertThat(user.getPassword()).startsWith("$2").isNotEqualTo("Abcdef12");
        ArgumentCaptor<List<SysUserRole>> roles = ArgumentCaptor.captor();
        verify(userRoleMapper).insert(roles.capture());
        assertThat(roles.getValue())
                .extracting(SysUserRole::getUserId, SysUserRole::getRoleId)
                .containsExactly(org.assertj.core.groups.Tuple.tuple(99L, 2L), org.assertj.core.groups.Tuple.tuple(99L, 3L));
    }

    @Test
    void createUserRejectsDuplicateUsername() {
        currentActor(1L, true);
        SysUser existing = new SysUser();
        existing.setUserId(5L);
        doReturn(existing).when(service).getOne(any());

        SysUser user = new SysUser();
        user.setUsername("admin");
        user.setPassword("Abcdef12");

        assertThatThrownBy(() -> service.createUser(user, null))
                .isInstanceOf(BusinessException.class)
                .hasMessage("用户名已存在");
        verify(service, never()).save(any(SysUser.class));
    }

    @Test
    void createUserRejectsWeakPassword() {
        currentActor(1L, true);
        doReturn(null).when(service).getOne(any());

        SysUser user = new SysUser();
        user.setUsername("neo");
        user.setPassword("short1");

        assertThatThrownBy(() -> service.createUser(user, null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("密码");
    }

    @Test
    void nonAdminCannotGrantAdminRole() {
        currentActor(2L, false);
        doReturn(null).when(service).getOne(any());
        SysRole adminRole = new SysRole();
        adminRole.setRoleId(1L);
        adminRole.setRoleKey("admin");
        when(roleService.listByIds(List.of(1L))).thenReturn(List.of(adminRole));

        SysUser user = new SysUser();
        user.setUsername("neo");
        user.setPassword("Abcdef12");

        assertThatThrownBy(() -> service.createUser(user, List.of(1L)))
                .isInstanceOf(BusinessException.class)
                .hasMessage("无权分配超级管理员角色");
        verify(service, never()).save(any(SysUser.class));
    }

    @Test
    void nonAdminCannotModifySuperAdminAccount() {
        currentActor(2L, false);
        stubGetById(1L, new SysUser());

        SysUser user = new SysUser();
        user.setUserId(1L);

        assertThatThrownBy(() -> service.updateUser(user, null))
                .isInstanceOf(BusinessException.class)
                .hasMessage("无权操作超级管理员账号");
        verify(service, never()).updateById(any(SysUser.class));
    }

    @Test
    void superAdminCannotBeDisabledViaUpdate() {
        currentActor(1L, true);
        stubGetById(1L, new SysUser());

        SysUser user = new SysUser();
        user.setUserId(1L);
        user.setStatus("1");

        assertThatThrownBy(() -> service.updateUser(user, null))
                .isInstanceOf(BusinessException.class)
                .hasMessage("不允许停用超级管理员");
    }

    @Test
    void updateUserReplacesRolesFullyAndNeverTouchesPassword() {
        currentActor(1L, true);
        stubGetById(5L, new SysUser());
        doReturn(null).when(service).getOne(any());
        doReturn(true).when(service).updateById(any(SysUser.class));

        SysUser user = new SysUser();
        user.setUserId(5L);
        user.setStatus("0");
        user.setPassword("AttackerInject12");
        service.updateUser(user, List.of(2L, 3L));

        ArgumentCaptor<SysUser> updated = ArgumentCaptor.forClass(SysUser.class);
        verify(service).updateById(updated.capture());
        // 基本信息 update 不允许改密码
        assertThat(updated.getValue().getPassword()).isNull();
        verify(userRoleMapper).delete(any());
        ArgumentCaptor<List<SysUserRole>> roles = ArgumentCaptor.captor();
        verify(userRoleMapper).insert(roles.capture());
        assertThat(roles.getValue()).hasSize(2);
        // 正常状态不应触发踢下线
        verify(stpLogic, never()).logout(any(Object.class));
    }

    @Test
    void changeUserStatusDisablingKicksOfflineImmediately() {
        currentActor(1L, true);
        stubGetById(5L, new SysUser());
        doReturn(true).when(service).updateById(any(SysUser.class));

        service.changeUserStatus(5L, "1");

        ArgumentCaptor<SysUser> updated = ArgumentCaptor.forClass(SysUser.class);
        verify(service).updateById(updated.capture());
        assertThat(updated.getValue().getStatus()).isEqualTo("1");
        verify(stpLogic).logout(5L);
    }

    @Test
    void changeUserStatusEnablingKeepsSession() {
        currentActor(1L, true);
        stubGetById(5L, new SysUser());
        doReturn(true).when(service).updateById(any(SysUser.class));

        service.changeUserStatus(5L, "0");

        verify(stpLogic, never()).logout(any(Object.class));
    }

    @Test
    void deleteUsersProtectsSuperAdmin() {
        currentActor(1L, true);

        assertThatThrownBy(() -> service.deleteUsers(List.of(1L, 5L)))
                .isInstanceOf(BusinessException.class)
                .hasMessage("不允许删除超级管理员");
        verify(service, never()).removeByIds(anyList());
    }

    @Test
    void deleteUsersRemovesRoleBindingsAndKicksAll() {
        currentActor(1L, true);
        doReturn(true).when(service).removeByIds(anyList());

        service.deleteUsers(List.of(5L, 6L));

        verify(service).removeByIds(List.of(5L, 6L));
        verify(userRoleMapper).delete(any());
        verify(stpLogic).logout(5L);
        verify(stpLogic).logout(6L);
    }

    @Test
    void updatePasswordRequiresCorrectOldPassword() {
        currentActor(5L, false);
        SysUser db = new SysUser();
        db.setUserId(5L);
        db.setPassword(OLD_PASSWORD_HASH);
        stubGetById(5L, db);

        assertThatThrownBy(() -> service.updatePassword(5L, "WrongPass12", "NewPass12"))
                .isInstanceOf(BusinessException.class)
                .hasMessage("原密码错误");
        verify(service, never()).updateById(any(SysUser.class));
    }

    @Test
    void updatePasswordEncodesNewPasswordAndKicksOffline() {
        currentActor(1L, true);
        SysUser db = new SysUser();
        db.setUserId(5L);
        db.setPassword(OLD_PASSWORD_HASH);
        stubGetById(5L, db);
        doReturn(true).when(service).updateById(any(SysUser.class));

        service.updatePassword(5L, OLD_PASSWORD, "NewPass12");

        ArgumentCaptor<SysUser> updated = ArgumentCaptor.forClass(SysUser.class);
        verify(service).updateById(updated.capture());
        assertThat(new BCryptPasswordEncoder().matches("NewPass12", updated.getValue().getPassword())).isTrue();
        verify(stpLogic).logout(5L);
    }
}
