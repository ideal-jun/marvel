package com.marvel.module.system.service;

import cn.dev33.satoken.stp.StpUtil;
import com.marvel.common.constant.Constants;
import com.marvel.module.system.entity.SysDept;
import com.marvel.module.system.entity.SysRole;
import com.marvel.module.system.entity.SysUser;
import com.marvel.module.system.mapper.SysDeptMapper;
import com.marvel.module.system.mapper.SysRoleDeptMapper;
import com.marvel.module.system.mapper.SysUserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 数据权限（@DataScope 的服务化实现）：按当前用户角色的 data_scope 计算可见部门集合。
 *
 * <p>取值：1=全部、2=自定义（sys_role_dept）、3=本部门、4=本部门及以下、5=仅本人。
 * 多角色取并集（最宽松）；缺省/未知取值按「本部门」保守处理，避免越权；
 * 无任何角色视为无可见范围（返回空部门集合）。
 */
@Service
@RequiredArgsConstructor
public class DataScopeService {

    private final SysRoleService roleService;
    private final SysRoleDeptMapper roleDeptMapper;
    private final SysDeptMapper deptMapper;
    private final SysUserMapper userMapper;

    /** 当前登录用户的数据范围 */
    public DataScope current() {
        return resolve(StpUtil.getLoginIdAsLong());
    }

    /** 指定用户的数据范围（便于单测） */
    public DataScope resolve(Long userId) {
        List<SysRole> roles = roleService.listRolesByUserId(userId);
        for (SysRole role : roles) {
            if (Constants.SUPER_ADMIN_ROLE.equals(role.getRoleKey())) {
                return DataScope.allData();
            }
        }

        Set<Long> deptIds = new HashSet<>();
        boolean selfOnly = false;
        boolean ownDept = false;
        boolean ownDeptAndBelow = false;
        for (SysRole role : roles) {
            String scope = role.getDataScope() == null ? "" : role.getDataScope();
            switch (scope) {
                case "1" -> {
                    return DataScope.allData();
                }
                case "2" -> deptIds.addAll(roleDeptMapper.selectDeptIdsByRoleId(role.getRoleId()));
                case "3" -> ownDept = true;
                case "4" -> ownDeptAndBelow = true;
                case "5" -> selfOnly = true;
                default -> ownDept = true;
            }
        }

        SysUser current = userMapper.selectById(userId);
        Long deptId = current == null ? null : current.getDeptId();
        if (deptId != null && ownDeptAndBelow) {
            deptIds.addAll(deptAndDescendants(deptId));
        }
        if (deptId != null && ownDept) {
            deptIds.add(deptId);
        }
        if (!deptIds.isEmpty()) {
            return DataScope.depts(deptIds);
        }
        if (selfOnly) {
            return DataScope.self(userId);
        }
        return DataScope.depts(Set.of());
    }

    /** 本部门及其全部后代部门（基于 ancestors 逗号字段精确匹配，避免 LIKE 误匹配） */
    private Set<Long> deptAndDescendants(Long deptId) {
        Set<Long> ids = new HashSet<>();
        ids.add(deptId);
        String target = String.valueOf(deptId);
        for (SysDept dept : deptMapper.selectList(null)) {
            String ancestors = dept.getAncestors();
            if (ancestors == null || dept.getDeptId() == null) {
                continue;
            }
            for (String part : ancestors.split(",")) {
                if (target.equals(part.trim())) {
                    ids.add(dept.getDeptId());
                    break;
                }
            }
        }
        return ids;
    }
}
