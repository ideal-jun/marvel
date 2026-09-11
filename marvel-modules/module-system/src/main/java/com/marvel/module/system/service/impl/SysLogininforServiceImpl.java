package com.marvel.module.system.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.marvel.module.system.entity.SysLogininfor;
import com.marvel.module.system.mapper.SysLogininforMapper;
import com.marvel.module.system.service.SysLogininforService;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;

@Service
public class SysLogininforServiceImpl extends ServiceImpl<SysLogininforMapper, SysLogininfor> implements SysLogininforService {

    @Override
    public IPage<SysLogininfor> pageLogs(long pageNum, long pageSize, String username, String ipaddr,
                                          String status, LocalDateTime beginTime, LocalDateTime endTime) {
        LambdaQueryWrapper<SysLogininfor> wrapper = new LambdaQueryWrapper<SysLogininfor>()
                .like(StringUtils.hasText(username), SysLogininfor::getUsername, username)
                .like(StringUtils.hasText(ipaddr), SysLogininfor::getIpaddr, ipaddr)
                .eq(StringUtils.hasText(status), SysLogininfor::getStatus, status)
                .ge(beginTime != null, SysLogininfor::getLoginTime, beginTime)
                .le(endTime != null, SysLogininfor::getLoginTime, endTime)
                .orderByDesc(SysLogininfor::getInfoId);
        return this.page(new Page<>(pageNum, pageSize), wrapper);
    }

    @Override
    public void clean() {
        this.getBaseMapper().delete(null);
    }
}
