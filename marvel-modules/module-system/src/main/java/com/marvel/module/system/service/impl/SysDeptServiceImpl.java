package com.marvel.module.system.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.marvel.common.exception.BusinessException;
import com.marvel.module.system.entity.SysDept;
import com.marvel.module.system.entity.SysUser;
import com.marvel.module.system.mapper.SysDeptMapper;
import com.marvel.module.system.mapper.SysUserMapper;
import com.marvel.module.system.service.DataScope;
import com.marvel.module.system.service.DataScopeService;
import com.marvel.module.system.service.SysDeptService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * 机构管理业务实现（机构树）。
 *
 * <p>层级为纯树：顶级机构（唯一根）→ 分公司 → 部门，不区分机构类型（只是叫法上的层级差异），
 * 采用「ancestors 祖级链」模型存储层级（如 0,100,101）。
 *
 * <p>规则：
 * <ul>
 *   <li>全库只有一个根（顶级机构，parentId=0），由初始化数据产生；新增只能挂到某个上级下；</li>
 *   <li>新增/编辑都可变更上级机构；变更时必须同步重写<b>全部后代</b>的 ancestors；</li>
 *   <li>禁止把上级设为自己或自己的下级；</li>
 *   <li>存在下级机构或已分配用户时不允许删除；顶级机构不允许删除、不允许移动。</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class SysDeptServiceImpl extends ServiceImpl<SysDeptMapper, SysDept> implements SysDeptService {

    /** 顶级机构的父级标识 */
    private static final long ROOT_PARENT_ID = 0L;

    /**
     * 机构最大层级深度：0=顶级机构，1=分公司，2=部门。
     * 也就是树最多到「部门」为止，部门下不能再有下级。
     */
    private static final int MAX_DEPTH = 2;

    private final DataScopeService dataScopeService;
    private final SysUserMapper userMapper;

    @Override
    public List<SysDept> listDeptTree(String deptName, String status) {
        List<SysDept> depts = list(new LambdaQueryWrapper<SysDept>()
                .like(StringUtils.hasText(deptName), SysDept::getDeptName, deptName)
                .eq(StringUtils.hasText(status), SysDept::getStatus, status)
                .orderByAsc(SysDept::getOrderNum));
        return filterByDataScope(depts);
    }

    /** 机构列表同样受数据权限约束 */
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
        if (!StringUtils.hasText(dept.getDeptName())) {
            throw new BusinessException("机构名称不能为空");
        }
        // 顶级机构唯一：只能挂到已有机构下，不允许新建根节点
        SysDept parent = validateAndGetParent(dept.getParentId(), null);
        dept.setAncestors(ancestorsOf(parent));
        dept.setDeptId(null);
        this.save(dept);
    }

    @Override
    @Transactional
    public void updateDept(SysDept dept) {
        SysDept db = getById(dept.getDeptId());
        if (db == null) {
            throw new BusinessException("机构不存在");
        }
        if (!StringUtils.hasText(dept.getDeptName())) {
            throw new BusinessException("机构名称不能为空");
        }
        if (isRoot(db)) {
            // 顶级机构不可移动：保持根位置与原祖级链
            dept.setParentId(ROOT_PARENT_ID);
            dept.setAncestors(null);
            this.updateById(dept);
            return;
        }
        boolean parentChanged = !Objects.equals(db.getParentId(), dept.getParentId());
        if (!parentChanged) {
            // 上级不变：置 null 交给 MyBatis-Plus 的 NOT_NULL 策略保留原 ancestors
            dept.setAncestors(null);
        } else {
            SysDept parent = validateAndGetParent(dept.getParentId(), dept.getDeptId());
            // 移动后整棵子树的最深层级不得超过「部门」
            if (depthOf(parent.getAncestors()) + 1 + subtreeHeight(db) > MAX_DEPTH) {
                throw new BusinessException("移动后机构层级超过部门");
            }
            dept.setAncestors(ancestorsOf(parent));
        }
        this.updateById(dept);
        if (parentChanged) {
            updateDescendantAncestors(db.getDeptId(), db.getAncestors(), dept.getAncestors());
        }
    }

    @Override
    public void deleteDept(Long deptId) {
        SysDept db = getById(deptId);
        if (db == null) {
            throw new BusinessException("机构不存在");
        }
        if (isRoot(db)) {
            throw new BusinessException("顶级机构不允许删除");
        }
        long children = count(new LambdaQueryWrapper<SysDept>().eq(SysDept::getParentId, deptId));
        if (children > 0) {
            throw new BusinessException("存在下级机构，不允许删除");
        }
        Long users = userMapper.selectCount(new LambdaQueryWrapper<SysUser>().eq(SysUser::getDeptId, deptId));
        if (users != null && users > 0) {
            throw new BusinessException("机构下存在用户，不允许删除");
        }
        this.removeById(deptId);
    }

    private boolean isRoot(SysDept dept) {
        return Objects.equals(dept.getParentId(), ROOT_PARENT_ID);
    }

    /**
     * 校验上级机构并返回它：必须存在、非根、非自身、非自己的下级，且层级未到「部门」。
     *
     * @param selfId 更新场景传入自身 ID，用于环检测；新建传 null
     */
    private SysDept validateAndGetParent(Long parentId, Long selfId) {
        if (parentId == null || parentId == ROOT_PARENT_ID) {
            throw new BusinessException("请选择上级机构");
        }
        if (parentId.equals(selfId)) {
            throw new BusinessException("上级机构不能为自身");
        }
        SysDept parent = getById(parentId);
        if (parent == null) {
            throw new BusinessException("上级机构不存在");
        }
        if (selfId != null && containsAncestor(parent.getAncestors(), selfId)) {
            throw new BusinessException("上级机构不能选择自己的下级");
        }
        if (depthOf(parent.getAncestors()) >= MAX_DEPTH) {
            throw new BusinessException("机构层级最多到部门");
        }
        return parent;
    }

    private String ancestorsOf(SysDept parent) {
        return parent.getAncestors() + "," + parent.getDeptId();
    }

    /** 层级深度：根（ancestors=0）为 0，每多一级 +1 */
    private int depthOf(String ancestors) {
        if (!StringUtils.hasText(ancestors)) {
            return 0;
        }
        return ancestors.split(",").length - 1;
    }

    /** 子树相对高度（叶子为 0），用于判断移动后是否会超过层级上限 */
    private int subtreeHeight(SysDept dept) {
        int currentDepth = depthOf(dept.getAncestors());
        String prefix = (dept.getAncestors() == null ? "" : dept.getAncestors()) + "," + dept.getDeptId();
        int maxDepth = currentDepth;
        List<SysDept> descendants = list(new LambdaQueryWrapper<SysDept>()
                .select(SysDept::getAncestors)
                .likeRight(SysDept::getAncestors, prefix));
        for (SysDept descendant : descendants) {
            maxDepth = Math.max(maxDepth, depthOf(descendant.getAncestors()));
        }
        return maxDepth - currentDepth;
    }

    /** ancestors 逗号串中是否包含指定机构（精确匹配，避免 LIKE 误判） */
    private boolean containsAncestor(String ancestors, Long deptId) {
        if (!StringUtils.hasText(ancestors)) {
            return false;
        }
        String target = String.valueOf(deptId);
        for (String part : ancestors.split(",")) {
            if (target.equals(part.trim())) {
                return true;
            }
        }
        return false;
    }

    /** 上级变更后，批量重写全部后代机构的 ancestors 前缀，保证层级链一致 */
    private void updateDescendantAncestors(Long deptId, String oldAncestors, String newAncestors) {
        String oldPrefix = (oldAncestors == null ? "" : oldAncestors) + "," + deptId;
        String newPrefix = newAncestors + "," + deptId;
        List<SysDept> descendants = list(new LambdaQueryWrapper<SysDept>()
                .select(SysDept::getDeptId, SysDept::getAncestors)
                .likeRight(SysDept::getAncestors, oldPrefix));
        for (SysDept descendant : descendants) {
            SysDept update = new SysDept();
            update.setDeptId(descendant.getDeptId());
            update.setAncestors(newPrefix + descendant.getAncestors().substring(oldPrefix.length()));
            this.updateById(update);
        }
    }
}
