package com.marvel.module.infra.service;

import org.springframework.core.io.Resource;
import org.springframework.web.multipart.MultipartFile;

/**
 * 文件存储抽象：本地实现见 LocalStorageService，未来可替换为 OSS/COS 实现。
 */
public interface StorageService {

    /** 上传并返回可访问的 URL */
    String upload(MultipartFile file) throws Exception;

    /** 按存储路径安全加载文件（越界路径必须拒绝） */
    Resource loadAsResource(String filePath);

    /** 按存储路径删除物理文件 */
    void delete(String filePath);
}
