package com.marvel.module.system.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.marvel.common.annotation.Log;
import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.marvel.common.result.R;
import com.marvel.module.system.dto.SysRoleVO;
import com.marvel.module.system.entity.SysUser;
import com.marvel.module.system.service.SysRoleService;
import com.marvel.module.system.service.SysUserService;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 用户管理接口，路径前缀 /system/**（与未来网关路由一致）。
 *
 * <p>权限模型：每个接口对应一个菜单按钮权限标识（system:user:xxx），
 * 由 Sa-Token 注解校验；登录校验由全局拦截器统一完成。
 */
@Validated
@RestController
@RequestMapping("/system/user")
@RequiredArgsConstructor
public class SysUserController {

    private final SysUserService userService;
    private final SysRoleService roleService;

    /** 分页查询用户列表，支持用户名/昵称模糊、状态精确、部门及其下级过滤 */
    @SaCheckPermission("system:user:list")
    @GetMapping("/page")
    public R<IPage<SysUser>> page(@RequestParam(defaultValue = "1") long pageNum,
                                  @RequestParam(defaultValue = "10") long pageSize,
                                  @RequestParam(required = false) String username,
                                  @RequestParam(required = false) String nickname,
                                  @RequestParam(required = false) String status,
                                  @RequestParam(required = false) Long deptId) {
        return R.ok(userService.pageUsers(pageNum, pageSize, username, nickname, status, deptId));
    }

    /** 用户详情（含已分配角色 ID），密码密文不外发 */
    @SaCheckPermission("system:user:query")
    @GetMapping("/{userId}")
    public R<Map<String, Object>> detail(@PathVariable Long userId) {
        SysUser user = userService.getById(userId);
        if (user == null) {
            return R.fail("用户不存在");
        }
        user.setPassword(null);
        return R.ok(Map.of(
                "user", user,
                "roleIds", userService.getRoleIdsByUserId(userId)
        ));
    }

    /** 新增用户，初始密码必填并做复杂度校验 */
    @SaCheckPermission("system:user:add")
    @Log(title = "用户管理", businessType = Log.BusinessType.INSERT)
    @PostMapping
    public R<Void> add(@RequestBody SysUser user,
                       @RequestParam(required = false) List<Long> roleIds) {
        userService.createUser(user, roleIds);
        return R.ok();
    }

    /** 修改用户基本信息与角色关联（不改动密码） */
    @SaCheckPermission("system:user:edit")
    @Log(title = "用户管理", businessType = Log.BusinessType.UPDATE)
    @PutMapping
    public R<Void> update(@RequestBody SysUser user,
                          @RequestParam(required = false) List<Long> roleIds) {
        userService.updateUser(user, roleIds);
        return R.ok();
    }

    /** 批量删除用户（超级管理员受保护） */
    @SaCheckPermission("system:user:remove")
    @Log(title = "用户管理", businessType = Log.BusinessType.DELETE)
    @DeleteMapping("/{userIds}")
    public R<Void> remove(@PathVariable List<Long> userIds) {
        userService.deleteUsers(userIds);
        return R.ok();
    }

    /** 管理员重置用户密码 */
    @SaCheckPermission("system:user:resetPwd")
    @Log(title = "用户管理", businessType = Log.BusinessType.UPDATE)
    @PutMapping("/resetPwd")
    public R<Void> resetPwd(@RequestParam Long userId,
                            @RequestParam @NotBlank @Size(min = 8, max = 32) String password) {
        userService.resetPassword(userId, password);
        return R.ok();
    }

    /** 当前登录用户修改自己的密码（需验证原密码） */
    @Log(title = "用户管理", businessType = Log.BusinessType.UPDATE)
    @PutMapping("/profile/password")
    public R<Void> changePwd(@RequestParam @NotBlank String oldPassword,
                             @RequestParam @NotBlank @Size(min = 8, max = 32) String newPassword) {
        userService.updatePassword(StpUtil.getLoginIdAsLong(), oldPassword, newPassword);
        return R.ok();
    }

    /** 启用/停用用户 */
    @SaCheckPermission("system:user:edit")
    @Log(title = "用户管理", businessType = Log.BusinessType.UPDATE)
    @PutMapping("/changeStatus")
    public R<Void> changeStatus(@RequestParam Long userId, @RequestParam String status) {
        userService.changeUserStatus(userId, status);
        return R.ok();
    }

    /** 查询用户已分配的角色 ID 列表 */
    @SaCheckPermission("system:user:query")
    @GetMapping("/roles/{userId}")
    public R<List<Long>> userRoles(@PathVariable Long userId) {
        return R.ok(userService.getRoleIdsByUserId(userId));
    }

    /** 角色下拉选项（用户管理页新增/编辑弹窗使用） */
    @SaCheckPermission("system:user:list")
    @GetMapping("/options/roles")
    public R<List<SysRoleVO>> roleOptions() {
        return R.ok(roleService.list().stream()
                .map(r -> new SysRoleVO(r.getRoleId(), r.getRoleName()))
                .toList());
    }
}
