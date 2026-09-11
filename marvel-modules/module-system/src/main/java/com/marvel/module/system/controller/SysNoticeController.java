package com.marvel.module.system.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.marvel.common.annotation.Log;
import com.marvel.common.result.R;
import com.marvel.module.system.entity.SysNotice;
import com.marvel.module.system.service.SysNoticeService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 通知公告接口，路径前缀 /system/**（与未来网关路由一致）。
 */
@RestController
@RequestMapping("/system/notice")
@RequiredArgsConstructor
public class SysNoticeController {

    private final SysNoticeService noticeService;

    @SaCheckPermission("system:notice:list")
    @GetMapping("/list")
    public R<List<SysNotice>> list(@RequestParam(required = false) String title,
                                   @RequestParam(required = false) String type) {
        return R.ok(noticeService.listNotices(title, type));
    }

    @SaCheckPermission("system:notice:query")
    @GetMapping("/{noticeId}")
    public R<SysNotice> detail(@PathVariable Long noticeId) {
        return R.ok(noticeService.getById(noticeId));
    }

    @SaCheckPermission("system:notice:add")
    @Log(title = "通知公告", businessType = Log.BusinessType.INSERT)
    @PostMapping
    public R<Void> add(@RequestBody SysNotice notice) {
        noticeService.createNotice(notice);
        return R.ok();
    }

    @SaCheckPermission("system:notice:edit")
    @Log(title = "通知公告", businessType = Log.BusinessType.UPDATE)
    @PutMapping
    public R<Void> update(@RequestBody SysNotice notice) {
        noticeService.updateNotice(notice);
        return R.ok();
    }

    @SaCheckPermission("system:notice:remove")
    @Log(title = "通知公告", businessType = Log.BusinessType.DELETE)
    @DeleteMapping("/{noticeIds}")
    public R<Void> remove(@PathVariable List<Long> noticeIds) {
        noticeService.deleteNotices(noticeIds);
        return R.ok();
    }
}
