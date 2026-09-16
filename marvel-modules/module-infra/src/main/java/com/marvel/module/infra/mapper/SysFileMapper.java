package com.marvel.module.infra.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.marvel.module.infra.entity.SysFile;
import org.apache.ibatis.annotations.Mapper;

@Mapper
/** 文件记录 Mapper。 */
public interface SysFileMapper extends BaseMapper<SysFile> {
}
