package com.marvel.module.system.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.marvel.common.annotation.Log;
import com.marvel.common.result.R;
import com.marvel.module.system.entity.SysOperLog;
import com.marvel.module.system.service.SysOperLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 操作日志接口：分页查询 / 批量删除 / 清空（只读审计数据不提供新增修改）。
 */
@RestController
@RequestMapping("/system/operlog")
@RequiredArgsConstructor
public class SysOperLogController {

    private final SysOperLogService operLogService;

    /** 分页查询（支持标题/操作人/状态/操作类型/时间范围过滤） */
    @SaCheckPermission("system:operlog:list")
    @GetMapping("/page")
    public R<IPage<SysOperLog>> page(@RequestParam(defaultValue = "1") long pageNum,
                                     @RequestParam(defaultValue = "10") long pageSize,
                                     @RequestParam(required = false) String title,
                                     @RequestParam(required = false) String operUser,
                                     @RequestParam(required = false) String status,
                                     @RequestParam(required = false) String businessType,
                                     @RequestParam(required = false)
                                     @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime beginTime,
                                     @RequestParam(required = false)
                                     @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endTime) {
        return R.ok(operLogService.pageLogs(pageNum, pageSize, title, operUser, status, businessType, beginTime, endTime));
    }

    /** 批量删除（路径传逗号分隔 id 列表） */
    @SaCheckPermission("system:operlog:remove")
    @Log(title = "操作日志", businessType = Log.BusinessType.DELETE)
    @DeleteMapping("/{operIds}")
    public R<Void> remove(@PathVariable List<Long> operIds) {
        operLogService.removeByIds(operIds);
        return R.ok();
    }

    /** 清空全部操作日志 */
    @SaCheckPermission("system:operlog:clean")
    @Log(title = "操作日志", businessType = Log.BusinessType.CLEAN)
    @DeleteMapping("/clean")
    public R<Void> clean() {
        operLogService.clean();
        return R.ok();
    }
}
