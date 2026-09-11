package com.marvel.module.system.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.marvel.module.system.entity.SysOperLog;
import com.marvel.module.system.mapper.SysOperLogMapper;
import com.marvel.module.system.service.SysOperLogService;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;

@Service
public class SysOperLogServiceImpl extends ServiceImpl<SysOperLogMapper, SysOperLog> implements SysOperLogService {

    @Override
    public IPage<SysOperLog> pageLogs(long pageNum, long pageSize, String title, String operUser,
                                       String status, String businessType, LocalDateTime beginTime, LocalDateTime endTime) {
        LambdaQueryWrapper<SysOperLog> wrapper = new LambdaQueryWrapper<SysOperLog>()
                .like(StringUtils.hasText(title), SysOperLog::getTitle, title)
                .like(StringUtils.hasText(operUser), SysOperLog::getOperUser, operUser)
                .eq(StringUtils.hasText(status), SysOperLog::getStatus, status)
                // method 列格式为 "TYPE 全限定方法名"，操作类型按前缀匹配
                .likeRight(StringUtils.hasText(businessType), SysOperLog::getMethod, businessType + " ")
                .ge(beginTime != null, SysOperLog::getOperTime, beginTime)
                .le(endTime != null, SysOperLog::getOperTime, endTime)
                .orderByDesc(SysOperLog::getOperId);
        return this.page(new Page<>(pageNum, pageSize), wrapper);
    }

    @Override
    public void clean() {
        this.getBaseMapper().delete(null);
    }
}
