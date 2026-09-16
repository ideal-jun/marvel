package com.marvel.module.system.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.marvel.common.exception.BusinessException;
import com.marvel.module.system.entity.SysDept;
import com.marvel.module.system.mapper.SysDeptMapper;
import com.marvel.module.system.service.DataScope;
import com.marvel.module.system.service.DataScopeService;
import com.marvel.module.system.service.SysDeptService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Set;

import java.util.List;

/**
 * 部门管理业务实现。
 *
 * <p>采用「ancestors 祖级链」模型存储层级（如 0,100,101），
 * 便于按任意部门聚合查询其下级，数据权限按部门过滤也依赖该字段。
 */
@Service
@RequiredArgsConstructor
public class SysDeptServiceImpl extends ServiceImpl<SysDeptMapper, SysDept> implements SysDeptService {

    private final DataScopeService dataScopeService;

    @Override
    public List<SysDept> listDeptTree(String deptName, String status) {
        List<SysDept> depts = list(new LambdaQueryWrapper<SysDept>()
                .like(StringUtils.hasText(deptName), SysDept::getDeptName, deptName)
                .eq(StringUtils.hasText(status), SysDept::getStatus, status)
                .orderByAsc(SysDept::getOrderNum));
        return filterByDataScope(depts);
    }

    /** 部门列表同样受数据权限约束 */
    private List<SysDept> filterByDataScope(List<SysDept> depts) {
        DataScope scope = dataScopeService.current();
        if (scope.all()) {
            return depts;
        }
        if (scope.selfUserId() != null) {
            return List.of();
        }
        Set<Long> deptIds = scope.deptIds();
        if (deptIds == null || deptIds.isEmpty()) {
            return List.of();
        }
        return depts.stream().filter(d -> deptIds.contains(d.getDeptId())).toList();
    }

    @Override
    public void createDept(SysDept dept) {
        SysDept parent = getById(dept.getParentId());
        if (parent == null) {
            throw new BusinessException("上级部门不存在");
        }
        dept.setDeptId(null);
        dept.setAncestors(parent.getAncestors() + "," + parent.getDeptId());
        this.save(dept);
    }

    @Override
    public void updateDept(SysDept dept) {
        SysDept db = getById(dept.getDeptId());
        if (db == null) {
            throw new BusinessException("部门不存在");
        }
        if (dept.getDeptId().equals(dept.getParentId())) {
            throw new BusinessException("上级部门不能为自身");
        }
        if (db.getParentId().equals(dept.getParentId())) {
            dept.setAncestors(null);
        } else {
            SysDept parent = getById(dept.getParentId());
            if (parent == null) {
                throw new BusinessException("上级部门不存在");
            }
            dept.setAncestors(parent.getAncestors() + "," + parent.getDeptId());
        }
        this.updateById(dept);
    }

    @Override
    public void deleteDept(Long deptId) {
        long children = count(new LambdaQueryWrapper<SysDept>().eq(SysDept::getParentId, deptId));
        if (children > 0) {
            throw new BusinessException("存在下级部门，不允许删除");
        }
        this.removeById(deptId);
    }
}
