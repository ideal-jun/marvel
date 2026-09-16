package com.marvel.module.system.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.dev33.satoken.session.SaSession;
import cn.dev33.satoken.stp.StpUtil;
import com.marvel.api.system.SystemApi;
import com.marvel.common.annotation.Log;
import com.marvel.common.constant.Constants;
import com.marvel.common.result.R;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 在线用户：基于 Sa-Token 令牌存储列出活跃会话，并支持强制下线。
 *
 * <p>安全取舍：接口只返回用户/时间/IP/UA 等审计信息，<b>不返回令牌本身</b>，
 * 避免管理员界面成为会话劫持的取数入口；强制下线按登录用户 ID 操作（共享令牌模式下即该用户全部会话）。
 */
@RestController
@RequestMapping("/system/online")
@RequiredArgsConstructor
public class SysOnlineController {

    /** 单次最多扫描的令牌数，避免全量扫描拖垮 Redis */
    private static final int MAX_SCAN = 1000;

    private final SystemApi systemApi;

    /** 在线用户列表（按登录用户去重） */
    @SaCheckPermission("system:online:list")
    @GetMapping("/list")
    public R<List<Map<String, Object>>> list() {
        // Redis 版 SaTokenDao.searchData 返回的是完整 Redis key（Authorization:login:token:<token>），
        // 需先去掉令牌 key 前缀再解析 loginId；内存 DAO 则直接返回 token 值。
        String tokenKeyPrefix = StpUtil.getStpLogic().splicingKeyTokenValue("");
        List<String> rawTokens = StpUtil.searchTokenValue("", 0, MAX_SCAN, true);
        Map<Long, Map<String, Object>> byUser = new LinkedHashMap<>();
        for (String raw : rawTokens) {
            String token = raw.startsWith(tokenKeyPrefix) ? raw.substring(tokenKeyPrefix.length()) : raw;
            Object loginId = StpUtil.getLoginIdByToken(token);
            if (loginId == null) {
                continue;
            }
            long userId = Long.parseLong(loginId.toString());
            if (byUser.containsKey(userId)) {
                continue;
            }
            SaSession session = StpUtil.getSessionByLoginId(loginId);
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("userId", userId);
            row.put("username", session == null ? null : session.get("username"));
            row.put("loginIp", session == null ? null : session.get("loginIp"));
            row.put("loginTime", session == null ? null : session.get("loginTime"));
            row.put("userAgent", session == null ? null : session.get("userAgent"));
            row.put("tokenTimeout", StpUtil.getTokenTimeout(token));
            byUser.put(userId, row);
        }
        return R.ok(new ArrayList<>(byUser.values()));
    }

    /** 强制指定用户全部会话下线（超级管理员账号受额外保护） */
    @SaCheckPermission("system:online:kickout")
    @Log(title = "在线用户", businessType = Log.BusinessType.FORCE)
    @DeleteMapping("/{userId}")
    public R<Void> kickout(@PathVariable Long userId) {
        if (Constants.SUPER_ADMIN_USER_ID.equals(userId)
                && !systemApi.getRoleKeysByUserId(StpUtil.getLoginIdAsLong()).contains(Constants.SUPER_ADMIN_ROLE)) {
            return R.fail("无权强制超级管理员下线");
        }
        StpUtil.kickout(userId);
        return R.ok();
    }
}
