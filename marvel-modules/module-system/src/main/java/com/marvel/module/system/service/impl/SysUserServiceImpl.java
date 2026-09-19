package com.marvel.module.system.service.impl;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.marvel.common.constant.Constants;
import com.marvel.common.exception.BusinessException;
import com.marvel.common.security.PasswordPolicy;
import com.marvel.common.validation.ContactPolicy;
import com.marvel.framework.config.CacheConfig;
import com.marvel.module.system.entity.SysDept;
import com.marvel.module.system.entity.SysRole;
import com.marvel.module.system.entity.SysUser;
import com.marvel.module.system.entity.SysUserRole;
import com.marvel.module.system.mapper.SysDeptMapper;
import com.marvel.module.system.mapper.SysUserMapper;
import com.marvel.module.system.mapper.SysUserRoleMapper;
import com.marvel.module.system.service.DataScope;
import com.marvel.module.system.service.DataScopeService;
import com.marvel.module.system.service.SysRoleService;
import com.marvel.module.system.service.SysUserService;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 用户管理业务实现。
 *
 * <p>关键规则：
 * <ul>
 *   <li>密码一律 BCrypt 单向加密存储，任何接口不回传密文；</li>
 *   <li>密码复杂度由服务端强制校验（长度 8-32，且同时包含字母与数字）；</li>
 *   <li>超级管理员（userId=1）受保护：禁止删除、禁止停用；</li>
 *   <li>非超级管理员不得操作超级管理员账号，也不得分配 admin 角色，防止垂直越权；</li>
 *   <li>用户-角色关联在事务内先删后插，保证与角色分配完全一致。</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class SysUserServiceImpl extends ServiceImpl<SysUserMapper, SysUser> implements SysUserService {

    private final SysUserRoleMapper userRoleMapper;
    private final SysDeptMapper deptMapper;
    private final SysRoleService roleService;
    private final DataScopeService dataScopeService;
    /** BCrypt 校验器无状态，可安全复用 */
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    /**
     * 分页查询用户。
     * 部门过滤采用 ancestors 前缀匹配，命中指定部门及其全部下级部门；
     * 参数均通过 #{?} 占位符绑定，不存在 SQL 注入风险。
     */
    @Override
    public IPage<SysUser> pageUsers(long pageNum, long pageSize, String username, String nickname, String status, Long deptId) {
        IPage<SysUser> page = this.page(new Page<>(pageNum, pageSize), buildUserQuery(username, nickname, status, deptId));
        // 列表数据统一抹除密码密文
        page.getRecords().forEach(u -> u.setPassword(null));
        return page;
    }

    @Override
    public List<SysUser> listForExport(String username, String nickname, String status, Long deptId) {
        // 导出上限 1 万行，避免误操作一次性拉爆内存
        List<SysUser> users = list(buildUserQuery(username, nickname, status, deptId).last("LIMIT 10000"));
        users.forEach(u -> u.setPassword(null));
        return users;
    }

    /** 用户列表/导出共用的查询条件：筛选 + 数据权限 */
    private LambdaQueryWrapper<SysUser> buildUserQuery(String username, String nickname, String status, Long deptId) {
        LambdaQueryWrapper<SysUser> wrapper = new LambdaQueryWrapper<SysUser>()
                .like(StringUtils.hasText(username), SysUser::getUsername, username)
                .like(StringUtils.hasText(nickname), SysUser::getNickname, nickname)
                .eq(StringUtils.hasText(status), SysUser::getStatus, status)
                .apply(deptId != null,
                        "dept_id IN (SELECT dept_id FROM sys_dept WHERE dept_id = {0} OR FIND_IN_SET({0}, ancestors) > 0)",
                        deptId)
                .orderByAsc(SysUser::getUserId);
        // 数据权限：按当前用户的 data_scope 收窄可见范围
        applyDataScope(wrapper);
        return wrapper;
    }

    @Override
    @Transactional
    public void createUser(SysUser user, List<Long> roleIds) {
        checkUsernameUnique(user.getUsername(), null);
        validatePassword(user.getPassword());
        validateContact(user.getEmail(), user.getPhone());
        checkPrivilegeEscalation(null, roleIds);
        user.setUserId(null);
        user.setPassword(passwordEncoder.encode(user.getPassword()));
        this.save(user);
        saveUserRoles(user.getUserId(), roleIds);
    }

    @Override
    @Transactional
    @CacheEvict(cacheNames = {CacheConfig.CACHE_USER_ROLES, CacheConfig.CACHE_USER_PERMS}, key = "#user.userId")
    public void updateUser(SysUser user, List<Long> roleIds) {
        SysUser db = getById(user.getUserId());
        if (db == null) {
            throw new BusinessException("用户不存在");
        }
        checkPrivilegeEscalation(user.getUserId(), roleIds);
        // 超级管理员状态保护，防止误操作锁死系统
        if (Constants.SUPER_ADMIN_USER_ID.equals(user.getUserId()) && StringUtils.hasText(user.getStatus())
                && !Constants.STATUS_NORMAL.equals(user.getStatus())) {
            throw new BusinessException("不允许停用超级管理员");
        }
        checkUsernameUnique(user.getUsername(), user.getUserId());
        validateContact(user.getEmail(), user.getPhone());
        // 基本信息 update 不允许改密码，密码变更走独立接口
        user.setPassword(null);
        this.updateById(user);
        // 角色全量替换：roleIds 为空集合表示清空角色（编辑弹窗可据此移除全部角色）
        rebuildUserRoles(user.getUserId(), roleIds);
        // 通过编辑接口停用用户时同样立即踢下线，避免停用后既有会话继续可用
        if (StringUtils.hasText(user.getStatus()) && !Constants.STATUS_NORMAL.equals(user.getStatus())) {
            forceLogout(user.getUserId());
        }
    }

    @Override
    public void changeUserStatus(Long userId, String status) {
        SysUser db = getById(userId);
        if (db == null) {
            throw new BusinessException("用户不存在");
        }
        checkPrivilegeEscalation(userId, null);
        if (Constants.SUPER_ADMIN_USER_ID.equals(userId) && !Constants.STATUS_NORMAL.equals(status)) {
            throw new BusinessException("不允许停用超级管理员");
        }
        SysUser update = new SysUser();
        update.setUserId(userId);
        update.setStatus(status);
        // 仅更新状态，绝不触碰角色关联
        this.updateById(update);
        // 停用即踢下线：否则已签发的 Sa-Token 会话在有效期（7 天）内仍可访问
        if (!Constants.STATUS_NORMAL.equals(status)) {
            forceLogout(userId);
        }
    }

    @Override
    @Transactional
    @CacheEvict(cacheNames = {CacheConfig.CACHE_USER_ROLES, CacheConfig.CACHE_USER_PERMS}, allEntries = true)
    public void deleteUsers(List<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return;
        }
        if (userIds.contains(Constants.SUPER_ADMIN_USER_ID)) {
            throw new BusinessException("不允许删除超级管理员");
        }
        this.removeByIds(userIds);
        userRoleMapper.delete(new LambdaQueryWrapper<SysUserRole>().in(SysUserRole::getUserId, userIds));
        // 删除即踢下线，避免被删除账号的令牌继续可用
        userIds.forEach(this::forceLogout);
    }

    @Override
    public void resetPassword(Long userId, String newPassword) {
        checkResetPasswordPermission(userId);
        validatePassword(newPassword);
        SysUser user = new SysUser();
        user.setUserId(userId);
        user.setPassword(passwordEncoder.encode(newPassword));
        this.updateById(user);
        // 密码重置后强制重新登录，避免旧令牌在密码泄露场景下继续可用
        forceLogout(userId);
    }

    @Override
    public void updatePassword(Long userId, String oldPassword, String newPassword) {
        SysUser db = getById(userId);
        // 使用常量时间比较语义的 matches，避免时序侧信道
        if (db == null || !passwordEncoder.matches(oldPassword, db.getPassword())) {
            throw new BusinessException("原密码错误");
        }
        validatePassword(newPassword);
        resetPassword(userId, newPassword);
    }

    @Override
    public SysUser getProfile(Long userId) {
        SysUser user = getById(userId);
        if (user == null) {
            throw new BusinessException("用户不存在");
        }
        // 个人资料接口绝不外发密码密文
        user.setPassword(null);
        // deptName 为非表字段，按 deptId 补充，供个人中心展示所属部门
        if (user.getDeptId() != null) {
            SysDept dept = deptMapper.selectById(user.getDeptId());
            user.setDeptName(dept == null ? null : dept.getDeptName());
        }
        return user;
    }

    @Override
    public void updateProfile(Long userId, SysUser profile) {
        SysUser db = getById(userId);
        if (db == null) {
            throw new BusinessException("用户不存在");
        }
        validateContact(profile.getEmail(), profile.getPhone());
        // 字段白名单：只允许本人维护展示类信息，账号/部门/状态/密码一律不在此接口变更
        SysUser update = new SysUser();
        update.setUserId(userId);
        update.setNickname(profile.getNickname());
        update.setEmail(profile.getEmail());
        update.setPhone(profile.getPhone());
        update.setSex(profile.getSex());
        update.setAvatar(profile.getAvatar());
        this.updateById(update);
    }

    @Override
    public List<Long> getRoleIdsByUserId(Long userId) {
        return userRoleMapper.selectList(new LambdaQueryWrapper<SysUserRole>().eq(SysUserRole::getUserId, userId))
                .stream().map(SysUserRole::getRoleId).toList();
    }

    /** 重建用户-角色关联：先删旧关联再批量插入，调用方需处于事务内 */
    private void rebuildUserRoles(Long userId, List<Long> roleIds) {
        userRoleMapper.delete(new LambdaQueryWrapper<SysUserRole>().eq(SysUserRole::getUserId, userId));
        saveUserRoles(userId, roleIds);
    }

    private void saveUserRoles(Long userId, List<Long> roleIds) {
        if (roleIds == null || roleIds.isEmpty()) {
            return;
        }
        List<SysUserRole> rows = new ArrayList<>(roleIds.size());
        for (Long roleId : roleIds) {
            SysUserRole ur = new SysUserRole();
            ur.setUserId(userId);
            ur.setRoleId(roleId);
            rows.add(ur);
        }
        // 批量插入，避免逐条往返数据库
        userRoleMapper.insert(rows);
    }

    /** 用户名唯一性校验，excludeUserId 用于更新场景排除自身 */
    private void checkUsernameUnique(String username, Long excludeUserId) {
        SysUser existing = getOne(new LambdaQueryWrapper<SysUser>().eq(SysUser::getUsername, username));
        if (existing != null && !existing.getUserId().equals(excludeUserId)) {
            throw new BusinessException("用户名已存在");
        }
    }

    /** 服务端密码复杂度校验，规则集中在 {@link PasswordPolicy}，便于单测与统一维护 */
    private void validatePassword(String password) {
        String error = PasswordPolicy.validate(password);
        if (error != null) {
            throw new BusinessException(error);
        }
    }

    /** 联系方式格式校验，规则集中在 {@link ContactPolicy} */
    private void validateContact(String email, String phone) {
        String emailError = ContactPolicy.validateEmail(email);
        if (emailError != null) {
            throw new BusinessException(emailError);
        }
        String phoneError = ContactPolicy.validatePhone(phone);
        if (phoneError != null) {
            throw new BusinessException(phoneError);
        }
    }

    /**
     * 垂直越权防护：非超级管理员不得操作超级管理员账号，也不得分配 admin 角色。
     * 否则仅拥有 user:edit 的普通管理员可把 admin 角色授予自己，进而取得全部权限。
     */
    private void checkPrivilegeEscalation(Long targetUserId, List<Long> roleIds) {
        if (hasSuperAdminRole(currentUserId())) {
            return;
        }
        if (targetUserId != null && Constants.SUPER_ADMIN_USER_ID.equals(targetUserId)) {
            throw new BusinessException("无权操作超级管理员账号");
        }
        if (roleIds != null && !roleIds.isEmpty()) {
            // 一次批量查询角色，避免按 roleId 逐条 getById
            Set<String> roleKeys = roleService.listByIds(roleIds).stream()
                    .map(SysRole::getRoleKey)
                    .collect(Collectors.toSet());
            if (roleKeys.contains(Constants.SUPER_ADMIN_ROLE)) {
                throw new BusinessException("无权分配超级管理员角色");
            }
        }
    }

    private void checkResetPasswordPermission(Long targetUserId) {
        if (hasSuperAdminRole(currentUserId())) {
            return;
        }
        if (Constants.SUPER_ADMIN_USER_ID.equals(targetUserId)) {
            throw new BusinessException("无权重置超级管理员密码");
        }
    }

    private boolean hasSuperAdminRole(Long userId) {
        return roleService.getRoleKeysByUserId(userId).contains(Constants.SUPER_ADMIN_ROLE);
    }

    private Long currentUserId() {
        return StpUtil.getLoginIdAsLong();
    }

    /** 将数据范围追加为查询条件；无可见范围时用恒假条件返回空集 */
    private void applyDataScope(LambdaQueryWrapper<SysUser> wrapper) {
        DataScope scope = dataScopeService.current();
        if (scope.all()) {
            return;
        }
        if (scope.selfUserId() != null) {
            wrapper.eq(SysUser::getUserId, scope.selfUserId());
            return;
        }
        Set<Long> deptIds = scope.deptIds();
        if (deptIds == null || deptIds.isEmpty()) {
            wrapper.eq(SysUser::getUserId, -1L);
            return;
        }
        wrapper.in(SysUser::getDeptId, deptIds);
    }

    /** 强制目标用户全部会话下线（停用 / 删除 / 重置密码后调用） */
    private void forceLogout(Long userId) {
        if (userId != null) {
            StpUtil.logout(userId);
        }
    }
}
