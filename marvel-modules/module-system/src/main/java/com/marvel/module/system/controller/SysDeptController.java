package com.marvel.module.system.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.marvel.common.annotation.Log;
import com.marvel.common.result.R;
import com.marvel.module.system.entity.SysDept;
import com.marvel.module.system.service.SysDeptService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 部门管理接口，路径前缀 /system/**（与未来网关路由一致）。
 */
@RestController
@RequestMapping("/system/dept")
@RequiredArgsConstructor
public class SysDeptController {

    private final SysDeptService deptService;

    /** 部门列表（平铺，含 ancestors 可在前端组树） */
    @SaCheckPermission("system:dept:list")
    @GetMapping("/list")
    public R<List<SysDept>> list(@RequestParam(required = false) String deptName,
                                 @RequestParam(required = false) String status) {
        return R.ok(deptService.listDeptTree(deptName, status));
    }

    @SaCheckPermission("system:dept:query")
    @GetMapping("/{deptId}")
    public R<SysDept> detail(@PathVariable Long deptId) {
        return R.ok(deptService.getById(deptId));
    }

    @SaCheckPermission("system:dept:add")
    @Log(title = "部门管理", businessType = Log.BusinessType.INSERT)
    @PostMapping
    public R<Void> add(@RequestBody SysDept dept) {
        deptService.createDept(dept);
        return R.ok();
    }

    @SaCheckPermission("system:dept:edit")
    @Log(title = "部门管理", businessType = Log.BusinessType.UPDATE)
    @PutMapping
    public R<Void> update(@RequestBody SysDept dept) {
        deptService.updateDept(dept);
        return R.ok();
    }

    @SaCheckPermission("system:dept:remove")
    @Log(title = "部门管理", businessType = Log.BusinessType.DELETE)
    @DeleteMapping("/{deptId}")
    public R<Void> remove(@PathVariable Long deptId) {
        deptService.deleteDept(deptId);
        return R.ok();
    }
}
