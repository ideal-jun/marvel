package com.marvel.module.system.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.marvel.common.annotation.Log;
import com.marvel.common.result.R;
import com.marvel.module.system.entity.SysConfig;
import com.marvel.module.system.service.SysConfigService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 系统参数接口，路径前缀 /system/**（与未来网关路由一致）。
 */
@RestController
@RequestMapping("/system/config")
@RequiredArgsConstructor
public class SysConfigController {

    private final SysConfigService configService;

    @SaCheckPermission("system:config:list")
    @GetMapping("/list")
    public R<List<SysConfig>> list(@RequestParam(required = false) String configName,
                                   @RequestParam(required = false) String configKey) {
        return R.ok(configService.listConfigs(configName, configKey, null));
    }

    /** 按键名取值：供业务/前端读取参数（登录即可） */
    @GetMapping("/key/{configKey}")
    public R<String> value(@PathVariable String configKey) {
        SysConfig config = configService.getByKey(configKey);
        return R.ok(config != null ? config.getConfigValue() : null);
    }

    @SaCheckPermission("system:config:query")
    @GetMapping("/{configId}")
    public R<SysConfig> detail(@PathVariable Long configId) {
        return R.ok(configService.getById(configId));
    }

    @SaCheckPermission("system:config:add")
    @Log(title = "参数管理", businessType = Log.BusinessType.INSERT)
    @PostMapping
    public R<Void> add(@RequestBody SysConfig config) {
        configService.createConfig(config);
        return R.ok();
    }

    @SaCheckPermission("system:config:edit")
    @Log(title = "参数管理", businessType = Log.BusinessType.UPDATE)
    @PutMapping
    public R<Void> update(@RequestBody SysConfig config) {
        configService.updateConfig(config);
        return R.ok();
    }

    @SaCheckPermission("system:config:remove")
    @Log(title = "参数管理", businessType = Log.BusinessType.DELETE)
    @DeleteMapping("/{configIds}")
    public R<Void> remove(@PathVariable List<Long> configIds) {
        configService.deleteConfigs(configIds);
        return R.ok();
    }
}
