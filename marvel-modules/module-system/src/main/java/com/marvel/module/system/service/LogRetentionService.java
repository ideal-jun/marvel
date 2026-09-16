package com.marvel.module.system.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.marvel.module.system.entity.SysLogininfor;
import com.marvel.module.system.entity.SysOperLog;
import com.marvel.module.system.mapper.SysLogininforMapper;
import com.marvel.module.system.mapper.SysOperLogMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * 日志保留期清理：每天 03:30 删除超过 {@code marvel.log.retention-days} 天的操作/登录日志，
 * 避免审计表无限增长。设为 <= 0 可关闭清理。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LogRetentionService {

    private final SysOperLogMapper operLogMapper;
    private final SysLogininforMapper logininforMapper;

    @Value("${marvel.log.retention-days:90}")
    private int retentionDays;

    @Scheduled(cron = "0 30 3 * * ?")
    public void cleanExpiredLogs() {
        if (retentionDays <= 0) {
            return;
        }
        LocalDateTime cutoff = LocalDateTime.now().minusDays(retentionDays);
        int operDeleted = operLogMapper.delete(
                new LambdaQueryWrapper<SysOperLog>().lt(SysOperLog::getOperTime, cutoff));
        int loginDeleted = logininforMapper.delete(
                new LambdaQueryWrapper<SysLogininfor>().lt(SysLogininfor::getLoginTime, cutoff));
        log.info("日志保留清理完成：操作日志 {} 条、登录日志 {} 条（保留 {} 天）",
                operDeleted, loginDeleted, retentionDays);
    }
}
