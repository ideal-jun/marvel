package com.marvel.module.system.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/** 角色-部门关联 Mapper（自定义数据范围 data_scope=2 使用）。 */
@Mapper
public interface SysRoleDeptMapper {

    @Select("SELECT dept_id FROM sys_role_dept WHERE role_id = #{roleId}")
    List<Long> selectDeptIdsByRoleId(@Param("roleId") Long roleId);
}
