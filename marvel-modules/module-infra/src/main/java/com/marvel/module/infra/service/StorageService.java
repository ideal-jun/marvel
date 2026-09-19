package com.marvel.module.infra.service;

import org.springframework.core.io.Resource;
import org.springframework.web.multipart.MultipartFile;

/**
 * 文件存储抽象：本地实现见 LocalStorageService，未来可替换为 OSS/COS 实现。
 */
public interface StorageService {

    /**
     * 上传文件并返回存储定位。
     *
     * <p>本地实现返回 {@code /uploads/...}；MinIO 实现返回对象 key（如 {@code 2026/09/18/uuid.png}）。
     * 需要给浏览器展示时请用 {@link #toUrl(String)} 转换，避免把临时地址写库。
     */
    String upload(MultipartFile file) throws Exception;

    /** 按存储路径安全加载文件（越界路径必须拒绝） */
    Resource loadAsResource(String filePath);

    /** 按存储路径删除物理文件 */
    void delete(String filePath);

    /**
     * 存储定位转浏览器可访问 URL：本地即原路径；MinIO 走公开直链或预签名地址。
     */
    default String toUrl(String path) {
        return path;
    }
}
