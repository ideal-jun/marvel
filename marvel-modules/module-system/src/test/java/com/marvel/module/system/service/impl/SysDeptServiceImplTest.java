package com.marvel.module.system.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.marvel.common.exception.BusinessException;
import com.marvel.module.system.entity.SysDept;
import com.marvel.module.system.entity.SysUser;
import com.marvel.module.system.mapper.SysUserMapper;
import com.marvel.module.system.service.DataScopeService;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 机构树单测（纯层级树）：唯一根、新增必须挂上级、移动重写 ancestors、环检测与删除保护。
 */
class SysDeptServiceImplTest {

    private SysUserMapper userMapper;
    private SysDeptServiceImpl service;

    @BeforeEach
    void setUp() {
        // 生产代码直接构造 LambdaQueryWrapper，需先注册实体元数据
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, SysDept.class);
        TableInfoHelper.initTableInfo(assistant, SysUser.class);
        userMapper = mock(SysUserMapper.class);
        service = spy(new SysDeptServiceImpl(mock(DataScopeService.class), userMapper));
    }

    private SysDept dept(Long id, Long parentId, String ancestors) {
        SysDept d = new SysDept();
        d.setDeptId(id);
        d.setParentId(parentId);
        d.setAncestors(ancestors);
        return d;
    }

    private SysDept form(Long parentId, String name) {
        SysDept d = new SysDept();
        d.setParentId(parentId);
        d.setDeptName(name);
        return d;
    }

    @Test
    void createRejectsBlankName() {
        assertThatThrownBy(() -> service.createDept(form(100L, " ")))
                .isInstanceOf(BusinessException.class)
                .hasMessage("机构名称不能为空");
    }

    @Test
    void createRequiresParent() {
        assertThatThrownBy(() -> service.createDept(form(0L, "研发部")))
                .isInstanceOf(BusinessException.class)
                .hasMessage("请选择上级机构");
    }

    @Test
    void createUnderParentBuildsAncestors() {
        doReturn(dept(100L, 0L, "0")).when(service).getById(100L);
        doReturn(true).when(service).save(any(SysDept.class));

        service.createDept(form(100L, "研发部"));

        ArgumentCaptor<SysDept> saved = ArgumentCaptor.forClass(SysDept.class);
        verify(service).save(saved.capture());
        assertThat(saved.getValue().getAncestors()).isEqualTo("0,100");
        assertThat(saved.getValue().getDeptId()).isNull();
    }

    @Test
    void createWithMissingParentRejected() {
        doReturn(null).when(service).getById(999L);

        assertThatThrownBy(() -> service.createDept(form(999L, "研发部")))
                .isInstanceOf(BusinessException.class)
                .hasMessage("上级机构不存在");
    }

    @Test
    void createUnderDepartmentRejected() {
        // 部门（第 3 层）下不能再新增
        doReturn(dept(103L, 101L, "0,100,101")).when(service).getById(103L);

        assertThatThrownBy(() -> service.createDept(form(103L, "后端小组")))
                .isInstanceOf(BusinessException.class)
                .hasMessage("机构层级最多到部门");
    }

    @Test
    void moveToTooDeepRejected() {
        // 把带下级的分公司移到另一分公司下 -> 子树将超过部门层级
        doReturn(dept(101L, 100L, "0,100")).when(service).getById(101L);
        doReturn(dept(200L, 100L, "0,100")).when(service).getById(200L);
        doReturn(List.of(dept(103L, 101L, "0,100,101"))).when(service).list(any(Wrapper.class));

        SysDept update = form(200L, "研发部");
        update.setDeptId(101L);
        assertThatThrownBy(() -> service.updateDept(update))
                .isInstanceOf(BusinessException.class)
                .hasMessage("移动后机构层级超过部门");
    }

    @Test
    void updateRejectsSelfAsParent() {
        doReturn(dept(101L, 100L, "0,100")).when(service).getById(101L);

        SysDept update = form(101L, "研发部");
        update.setDeptId(101L);
        assertThatThrownBy(() -> service.updateDept(update))
                .isInstanceOf(BusinessException.class)
                .hasMessage("上级机构不能为自身");
    }

    @Test
    void updateRejectsDescendantAsParent() {
        doReturn(dept(101L, 100L, "0,100")).when(service).getById(101L);
        doReturn(dept(103L, 101L, "0,100,101")).when(service).getById(103L);

        SysDept update = form(103L, "研发部");
        update.setDeptId(101L);
        assertThatThrownBy(() -> service.updateDept(update))
                .isInstanceOf(BusinessException.class)
                .hasMessage("上级机构不能选择自己的下级");
    }

    @Test
    void updateRewritesDescendantAncestorsWhenMoved() {
        // 把 101（原在 100 下）移动到 200 下
        doReturn(dept(101L, 100L, "0,100")).when(service).getById(101L);
        doReturn(dept(200L, 0L, "0")).when(service).getById(200L);
        doReturn(List.of(dept(103L, 101L, "0,100,101"))).when(service).list(any(Wrapper.class));
        doReturn(true).when(service).updateById(any(SysDept.class));

        SysDept update = form(200L, "研发部");
        update.setDeptId(101L);
        service.updateDept(update);

        ArgumentCaptor<SysDept> updates = ArgumentCaptor.forClass(SysDept.class);
        verify(service, atLeastOnce()).updateById(updates.capture());
        SysDept descendantUpdate = updates.getAllValues().stream()
                .filter(u -> u.getDeptId().equals(103L))
                .findFirst()
                .orElseThrow();
        assertThat(descendantUpdate.getAncestors()).isEqualTo("0,200,101");
    }

    @Test
    void updateKeepingParentPreservesAncestors() {
        doReturn(dept(101L, 100L, "0,100")).when(service).getById(101L);
        doReturn(true).when(service).updateById(any(SysDept.class));

        SysDept update = form(100L, "研发部");
        update.setDeptId(101L);
        service.updateDept(update);

        ArgumentCaptor<SysDept> updates = ArgumentCaptor.forClass(SysDept.class);
        verify(service).updateById(updates.capture());
        assertThat(updates.getValue().getAncestors()).isNull();
    }

    @Test
    void updateRootKeepsRootPosition() {
        doReturn(dept(100L, 0L, "0")).when(service).getById(100L);
        doReturn(true).when(service).updateById(any(SysDept.class));

        // 试图把顶级机构移到别处：应被忽略，保持根位置
        SysDept update = form(200L, "Marvel 科技");
        update.setDeptId(100L);
        service.updateDept(update);

        ArgumentCaptor<SysDept> updates = ArgumentCaptor.forClass(SysDept.class);
        verify(service).updateById(updates.capture());
        assertThat(updates.getValue().getParentId()).isZero();
        assertThat(updates.getValue().getAncestors()).isNull();
    }

    @Test
    void deleteBlockedWhenChildrenExist() {
        doReturn(dept(101L, 100L, "0,100")).when(service).getById(101L);
        doReturn(2L).when(service).count(any());

        assertThatThrownBy(() -> service.deleteDept(101L))
                .isInstanceOf(BusinessException.class)
                .hasMessage("存在下级机构，不允许删除");
        verify(service, never()).removeById(101L);
    }

    @Test
    void deleteBlockedWhenUsersAssigned() {
        doReturn(dept(101L, 100L, "0,100")).when(service).getById(101L);
        doReturn(0L).when(service).count(any());
        when(userMapper.selectCount(any())).thenReturn(3L);

        assertThatThrownBy(() -> service.deleteDept(101L))
                .isInstanceOf(BusinessException.class)
                .hasMessage("机构下存在用户，不允许删除");
        verify(service, never()).removeById(101L);
    }

    @Test
    void deleteRootBlocked() {
        doReturn(dept(100L, 0L, "0")).when(service).getById(100L);

        assertThatThrownBy(() -> service.deleteDept(100L))
                .isInstanceOf(BusinessException.class)
                .hasMessage("顶级机构不允许删除");
        verify(service, never()).removeById(100L);
    }

    @Test
    void deleteSucceedsWhenLeafAndUnused() {
        doReturn(dept(101L, 100L, "0,100")).when(service).getById(101L);
        doReturn(0L).when(service).count(any());
        when(userMapper.selectCount(any())).thenReturn(0L);
        doReturn(true).when(service).removeById(101L);

        service.deleteDept(101L);

        verify(service).removeById(101L);
    }
}
