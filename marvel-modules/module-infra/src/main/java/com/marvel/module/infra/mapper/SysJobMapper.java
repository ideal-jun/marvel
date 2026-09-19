package com.marvel.module.infra.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.marvel.module.infra.entity.SysJob;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface SysJobMapper extends BaseMapper<SysJob> {

    /** 启用中的任务数（0=正常 1=暂停） */
    @Select("SELECT COUNT(*) FROM sys_job WHERE status = '0'")
    long countEnabled();
}
