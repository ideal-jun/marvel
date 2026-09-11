package com.marvel.module.system.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.spring.service.IService;
import com.marvel.module.system.entity.SysLogininfor;

import java.time.LocalDateTime;

/**
 * 登录日志 Service。
 */
public interface SysLogininforService extends IService<SysLogininfor> {

    /**
     * 分页查询登录日志。
     *
     * @param username  用户账号（模糊）
     * @param ipaddr    登录 IP（模糊）
     * @param status    状态（0/1）
     * @param beginTime 登录时间起（含）
     * @param endTime   登录时间止（含）
     */
    IPage<SysLogininfor> pageLogs(long pageNum, long pageSize, String username, String ipaddr,
                                  String status, LocalDateTime beginTime, LocalDateTime endTime);

    /** 清空全部登录日志 */
    void clean();
}
