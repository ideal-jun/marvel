package com.marvel.module.infra.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.marvel.module.infra.entity.SysJobLog;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

@Mapper
public interface SysJobLogMapper extends BaseMapper<SysJobLog> {

    /** 按状态统计执行次数（0=成功 1=失败），供看板聚合 */
    @Select("SELECT status AS s, COUNT(*) AS c FROM sys_job_log GROUP BY status")
    List<Map<String, Object>> countByStatus();
}
