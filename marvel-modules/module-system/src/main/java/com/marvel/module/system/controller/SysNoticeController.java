package com.marvel.module.system.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.marvel.common.annotation.Log;
import com.marvel.common.result.R;
import com.marvel.module.system.entity.SysNotice;
import com.marvel.module.system.service.SysNoticeService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

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

    /* ---------------- 站内消息（登录即可，个人维度） ---------------- */

    /** 我的消息：已发布公告 + 当前用户已读状态（支持标题/类型筛选） */
    @GetMapping("/my")
    public R<IPage<Map<String, Object>>> my(@RequestParam(defaultValue = "1") long pageNum,
                                            @RequestParam(defaultValue = "10") long pageSize,
                                            @RequestParam(defaultValue = "false") boolean onlyUnread,
                                            @RequestParam(required = false) String title,
                                            @RequestParam(required = false) String type) {
        return R.ok(noticeService.myNotices(StpUtil.getLoginIdAsLong(), pageNum, pageSize, onlyUnread, title, type));
    }

    /** 未读消息数（用于顶栏红点） */
    @GetMapping("/unread-count")
    public R<Long> unreadCount() {
        return R.ok(noticeService.unreadCount(StpUtil.getLoginIdAsLong()));
    }

    /** 标记单条消息已读（幂等） */
    @PostMapping("/read/{noticeId}")
    public R<Void> read(@PathVariable Long noticeId) {
        noticeService.markRead(StpUtil.getLoginIdAsLong(), noticeId);
        return R.ok();
    }

    /** 全部标记已读 */
    @PostMapping("/read-all")
    public R<Void> readAll() {
        noticeService.markAllRead(StpUtil.getLoginIdAsLong());
        return R.ok();
    }

    /** 删除我的消息（仅影响当前用户可见性，不删除公告本身） */
    @DeleteMapping("/my/{noticeIds}")
    public R<Void> deleteMy(@PathVariable List<Long> noticeIds) {
        noticeService.deleteMyNotices(StpUtil.getLoginIdAsLong(), noticeIds);
        return R.ok();
    }
}
