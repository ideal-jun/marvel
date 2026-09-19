package com.marvel.module.infra.service;

import com.marvel.common.exception.BusinessException;
import com.marvel.module.infra.entity.SysFile;
import com.marvel.module.infra.mapper.SysFileMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;

/**
 * 本地磁盘存储实现（默认，marvel.storage.type=local）。
 *
 * <p>上传安全控制见 {@link StorageSupport}。对象存储实现见 {@link MinioStorageService}。
 */
@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "marvel.storage.type", havingValue = "local", matchIfMissing = true)
public class LocalStorageService implements StorageService {

    private final SysFileMapper fileMapper;

    @Value("${marvel.storage.local.path:./uploads}")
    private String basePath;

    @Override
    public String upload(MultipartFile file) {
        String ext = StorageSupport.validate(file, StorageSupport.ALLOWED_EXTENSIONS);
        String datePath = StorageSupport.datePath();
        String filename = UUID.randomUUID().toString().replace("-", "") + "." + ext;
        Path target = Paths.get(basePath, datePath, filename).normalize();
        // 双重确认目标路径仍位于存储根目录内，防止路径穿越
        if (!target.startsWith(Paths.get(basePath).normalize())) {
            throw new BusinessException("非法的存储路径");
        }
        try {
            Files.createDirectories(target.getParent());
            file.transferTo(target.toAbsolutePath());
        } catch (IOException e) {
            log.error("文件上传失败", e);
            throw new BusinessException("文件上传失败，请稍后重试");
        }

        String url = "/uploads/" + datePath + "/" + filename;
        SysFile record = new SysFile();
        record.setFileName(StorageSupport.displayName(file, filename));
        record.setFilePath(url);
        record.setFileSize(file.getSize());
        record.setContentType(file.getContentType());
        fileMapper.insert(record);
        return url;
    }

    @Override
    public Resource loadAsResource(String filePath) {
        Path path = resolveSafely(filePath);
        if (!Files.exists(path) || Files.isDirectory(path)) {
            throw new BusinessException("文件不存在");
        }
        return new FileSystemResource(path);
    }

    @Override
    public void delete(String filePath) {
        Path path = resolveSafely(filePath);
        try {
            Files.deleteIfExists(path);
        } catch (IOException e) {
            log.error("文件删除失败: {}", path, e);
            throw new BusinessException("文件删除失败");
        }
    }

    /** 把 /uploads/xxx 安全解析到存储根目录之内，拒绝路径穿越与非法前缀 */
    private Path resolveSafely(String filePath) {
        if (!StringUtils.hasText(filePath) || !filePath.startsWith("/uploads/")) {
            throw new BusinessException("非法的文件路径");
        }
        Path rootPath = Paths.get(basePath).toAbsolutePath().normalize();
        Path target = rootPath.resolve(filePath.substring("/uploads/".length())).normalize();
        if (!target.startsWith(rootPath)) {
            throw new BusinessException("非法的文件路径");
        }
        return target;
    }
}
