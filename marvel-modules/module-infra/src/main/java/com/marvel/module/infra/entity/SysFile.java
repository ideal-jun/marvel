package com.marvel.module.infra.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 文件记录（sys_file）：上传文件的元数据，用于文件治理与审计。 */
@Data
@TableName("sys_file")
public class SysFile {

    @TableId(type = IdType.AUTO)
    private Long fileId;

    /** 原始文件名（仅用于展示，不参与存储路径） */
    private String fileName;

    /** 访问路径 /uploads/yyyy/MM/dd/uuid.ext */
    private String filePath;

    private Long fileSize;

    private String contentType;

    private LocalDateTime createTime;
}
