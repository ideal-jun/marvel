package com.marvel.module.system.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.marvel.common.annotation.Log;
import com.marvel.common.result.R;
import com.marvel.module.system.entity.SysLogininfor;
import com.marvel.module.system.service.SysLogininforService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 登录日志接口：分页查询 / 批量删除 / 清空。
 */
@RestController
@RequestMapping("/system/logininfor")
@RequiredArgsConstructor
public class SysLogininforController {

    private final SysLogininforService logininforService;

    /** 分页查询（支持账号/IP/状态/时间范围过滤） */
    @SaCheckPermission("system:logininfor:list")
    @GetMapping("/page")
    public R<IPage<SysLogininfor>> page(@RequestParam(defaultValue = "1") long pageNum,
                                        @RequestParam(defaultValue = "10") long pageSize,
                                        @RequestParam(required = false) String username,
                                        @RequestParam(required = false) String ipaddr,
                                        @RequestParam(required = false) String status,
                                        @RequestParam(required = false)
                                        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime beginTime,
                                        @RequestParam(required = false)
                                        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endTime) {
        return R.ok(logininforService.pageLogs(pageNum, pageSize, username, ipaddr, status, beginTime, endTime));
    }

    /** 批量删除（路径传逗号分隔 id 列表） */
    @SaCheckPermission("system:logininfor:remove")
    @Log(title = "登录日志", businessType = Log.BusinessType.DELETE)
    @DeleteMapping("/{infoIds}")
    public R<Void> remove(@PathVariable List<Long> infoIds) {
        logininforService.removeByIds(infoIds);
        return R.ok();
    }

    /** 清空全部登录日志 */
    @SaCheckPermission("system:logininfor:clean")
    @Log(title = "登录日志", businessType = Log.BusinessType.CLEAN)
    @DeleteMapping("/clean")
    public R<Void> clean() {
        logininforService.clean();
        return R.ok();
    }
}
