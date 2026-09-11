package com.marvel.module.system.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.marvel.common.annotation.Log;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.marvel.common.result.R;
import com.marvel.module.system.entity.SysRole;
import com.marvel.module.system.service.SysRoleService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 角色管理接口，路径前缀 /system/**（与未来网关路由一致）。
 */
@RestController
@RequestMapping("/system/role")
@RequiredArgsConstructor
public class SysRoleController {

    private final SysRoleService roleService;

    /** 分页查询角色列表 */
    @SaCheckPermission("system:role:list")
    @GetMapping("/page")
    public R<IPage<SysRole>> page(@RequestParam(defaultValue = "1") long pageNum,
                                  @RequestParam(defaultValue = "10") long pageSize,
                                  @RequestParam(required = false) String roleName,
                                  @RequestParam(required = false) String roleKey,
                                  @RequestParam(required = false) String status) {
        return R.ok(roleService.pageRoles(pageNum, pageSize, roleName, roleKey, status));
    }

    @SaCheckPermission("system:role:list")
    @GetMapping("/list")
    public R<List<SysRole>> list() {
        return R.ok(roleService.list());
    }

    @SaCheckPermission("system:role:query")
    @GetMapping("/{roleId}")
    public R<SysRole> detail(@PathVariable Long roleId) {
        return R.ok(roleService.getById(roleId));
    }

    @SaCheckPermission("system:role:query")
    @GetMapping("/{roleId}/menuIds")
    public R<List<Long>> menuIds(@PathVariable Long roleId) {
        return R.ok(roleService.getMenuIdsByRoleId(roleId));
    }

    @SaCheckPermission("system:role:add")
    @Log(title = "角色管理", businessType = Log.BusinessType.INSERT)
    @PostMapping
    public R<Void> add(@RequestBody SysRole role) {
        roleService.createRole(role);
        return R.ok();
    }

    @SaCheckPermission("system:role:edit")
    @Log(title = "角色管理", businessType = Log.BusinessType.UPDATE)
    @PutMapping
    public R<Void> update(@RequestBody SysRole role) {
        roleService.updateRole(role);
        return R.ok();
    }

    @SaCheckPermission("system:role:remove")
    @Log(title = "角色管理", businessType = Log.BusinessType.DELETE)
    @DeleteMapping("/{roleIds}")
    public R<Void> remove(@PathVariable List<Long> roleIds) {
        roleService.deleteRoles(roleIds);
        return R.ok();
    }
}
