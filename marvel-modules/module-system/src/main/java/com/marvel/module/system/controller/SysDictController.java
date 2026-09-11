package com.marvel.module.system.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.marvel.common.annotation.Log;
import com.marvel.common.result.R;
import com.marvel.module.system.entity.SysDictData;
import com.marvel.module.system.entity.SysDictType;
import com.marvel.module.system.service.SysDictService;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 字典管理接口，路径前缀 /system/**（与未来网关路由一致）。
 * 类型与数据合并在一个控制器内，前端主从页面一次加载两侧数据。
 */
@Validated
@RestController
@RequiredArgsConstructor
public class SysDictController {

    private final SysDictService dictService;

    /* ---------------- 字典类型 ---------------- */

    @SaCheckPermission("system:dict:list")
    @GetMapping("/system/dict/type/list")
    public R<List<SysDictType>> listTypes(@RequestParam(required = false) String dictName,
                                          @RequestParam(required = false) String dictType,
                                          @RequestParam(required = false) String status) {
        return R.ok(dictService.listTypes(dictName, dictType, status));
    }

    @SaCheckPermission("system:dict:add")
    @Log(title = "字典管理", businessType = Log.BusinessType.INSERT)
    @PostMapping("/system/dict/type")
    public R<Void> addType(@RequestBody SysDictType dictType) {
        dictService.createType(dictType);
        return R.ok();
    }

    @SaCheckPermission("system:dict:edit")
    @Log(title = "字典管理", businessType = Log.BusinessType.UPDATE)
    @PutMapping("/system/dict/type")
    public R<Void> updateType(@RequestBody SysDictType dictType) {
        dictService.updateType(dictType);
        return R.ok();
    }

    @SaCheckPermission("system:dict:remove")
    @Log(title = "字典管理", businessType = Log.BusinessType.DELETE)
    @DeleteMapping("/system/dict/type/{dictId}")
    public R<Void> removeType(@PathVariable Long dictId) {
        dictService.deleteType(dictId);
        return R.ok();
    }

    /* ---------------- 字典数据 ---------------- */

    /** 按字典类型查询数据（keyword 模糊匹配 label/value），供主从页面右侧表格使用 */
    @SaCheckPermission("system:dict:list")
    @GetMapping("/system/dict/data/list")
    public R<List<SysDictData>> listData(@RequestParam @NotBlank String dictType,
                                         @RequestParam(required = false) String keyword) {
        return R.ok(dictService.listData(dictType, keyword));
    }

    /** 供业务下拉使用的启用字典数据（登录即可调用，无需按钮权限） */
    @GetMapping("/system/dict/data/enabled/{dictType}")
    public R<List<SysDictData>> enabledData(@PathVariable String dictType) {
        return R.ok(dictService.listEnabledData(dictType));
    }

    @SaCheckPermission("system:dict:add")
    @Log(title = "字典管理", businessType = Log.BusinessType.INSERT)
    @PostMapping("/system/dict/data")
    public R<Void> addData(@RequestBody SysDictData dictData) {
        dictService.createData(dictData);
        return R.ok();
    }

    @SaCheckPermission("system:dict:edit")
    @Log(title = "字典管理", businessType = Log.BusinessType.UPDATE)
    @PutMapping("/system/dict/data")
    public R<Void> updateData(@RequestBody SysDictData dictData) {
        dictService.updateData(dictData);
        return R.ok();
    }

    @SaCheckPermission("system:dict:remove")
    @Log(title = "字典管理", businessType = Log.BusinessType.DELETE)
    @DeleteMapping("/system/dict/data/{dictCodes}")
    public R<Void> removeData(@PathVariable List<Long> dictCodes) {
        dictService.deleteData(dictCodes);
        return R.ok();
    }
}
