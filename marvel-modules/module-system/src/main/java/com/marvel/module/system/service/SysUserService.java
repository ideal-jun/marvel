package com.marvel.module.system.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.spring.service.IService;
import com.marvel.module.system.entity.SysUser;

import java.util.List;

/** 用户管理服务接口。 */
public interface SysUserService extends IService<SysUser> {

    IPage<SysUser> pageUsers(long pageNum, long pageSize, String username, String nickname, String status, Long deptId);

    /** 导出用：与列表相同的筛选条件 + 数据权限，返回全部匹配用户（不分页） */
    List<SysUser> listForExport(String username, String nickname, String status, Long deptId);

    void createUser(SysUser user, List<Long> roleIds);

    void updateUser(SysUser user, List<Long> roleIds);

    /** 仅变更用户状态，不触碰角色关联（区别于 updateUser 的全量基本信息更新） */
    void changeUserStatus(Long userId, String status);

    void deleteUsers(List<Long> userIds);

    void resetPassword(Long userId, String newPassword);

    void updatePassword(Long userId, String oldPassword, String newPassword);

    List<Long> getRoleIdsByUserId(Long userId);
}
