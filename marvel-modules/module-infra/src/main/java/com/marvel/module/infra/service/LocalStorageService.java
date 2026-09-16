package com.marvel.module.infra.service;

import com.marvel.common.exception.BusinessException;
import com.marvel.module.infra.entity.SysFile;
import com.marvel.module.infra.mapper.SysFileMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/**
 * 本地磁盘存储实现（预留 OSS/COS 扩展点，实现 {@link StorageService} 即可替换）。
 *
 * <p>上传安全控制（渗透测试重点项）：
 * <ul>
 *   <li>扩展名白名单 + 严格格式校验，拒绝 jsp/exe 等可执行或脚本类文件；</li>
 *   <li>磁盘文件名统一使用 UUID 重写，用户可控的原始文件名不参与存储路径，杜绝路径穿越；</li>
 *   <li>按日期分目录存储，避免单目录文件过多。</li>
 * </ul>
 *
 * <p>上传成功后写入 sys_file 元数据，便于文件治理与审计。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LocalStorageService implements StorageService {

    /** 允许上传的扩展名白名单 */
    private static final Set<String> ALLOWED_EXTENSIONS = Set.of(
            "jpg", "jpeg", "png", "gif", "webp",
            "pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "txt", "md", "zip");

    /** 扩展名合法性（字母数字，最长 10 位），二次防御异常输入 */
    private static final String EXT_PATTERN = "^[a-zA-Z0-9]{1,10}$";

    private final SysFileMapper fileMapper;

    @Value("${marvel.storage.local.path:./uploads}")
    private String basePath;

    @Override
    public String upload(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException("上传文件不能为空");
        }
        String originalFilename = file.getOriginalFilename();
        String ext = extractExtension(originalFilename);
        if (!ALLOWED_EXTENSIONS.contains(ext)) {
            throw new BusinessException("不支持的文件类型：" + ext);
        }
        // SVG 可内嵌 <script>，而 /uploads/** 同源公开托管，会形成存储型 XSS，明确拒绝
        String contentType = file.getContentType();
        if (contentType != null && contentType.toLowerCase(Locale.ROOT).contains("svg")) {
            throw new BusinessException("不支持的文件类型：svg");
        }

        String datePath = LocalDate.now().toString().replace("-", "/");
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
        record.setFileName(StringUtils.cleanPath(originalFilename == null ? filename : originalFilename));
        record.setFilePath(url);
        record.setFileSize(file.getSize());
        record.setContentType(contentType);
        fileMapper.insert(record);
        return url;
    }

    /**
     * 提取并校验文件扩展名；无扩展名或格式非法时抛出业务异常。
     */
    private String extractExtension(String originalFilename) {
        String name = StringUtils.cleanPath(originalFilename == null ? "" : originalFilename);
        int dot = name.lastIndexOf('.');
        if (dot < 0 || dot == name.length() - 1) {
            throw new BusinessException("文件缺少扩展名");
        }
        String ext = name.substring(dot + 1).toLowerCase(Locale.ROOT);
        if (!ext.matches(EXT_PATTERN)) {
            throw new BusinessException("文件扩展名不合法");
        }
        return ext;
    }
}
