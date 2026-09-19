package com.marvel.module.infra.service;

import com.marvel.common.exception.BusinessException;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.util.Locale;
import java.util.Set;

/**
 * 存储实现的公共校验与命名逻辑，供本地磁盘与 MinIO 实现复用。
 *
 * <p>上传安全控制（渗透测试重点项）：
 * <ul>
 *   <li>扩展名白名单 + 格式二次校验，拒绝 jsp/exe 等可执行或脚本类文件；</li>
 *   <li>拒绝 svg（可内嵌脚本，公开托管会形成存储型 XSS）；</li>
 *   <li>磁盘/对象名统一 UUID 重写，用户可控的原始文件名不参与存储定位，杜绝路径穿越。</li>
 * </ul>
 */
public final class StorageSupport {

    /** 允许上传的扩展名白名单 */
    public static final Set<String> ALLOWED_EXTENSIONS = Set.of(
            "jpg", "jpeg", "png", "gif", "webp",
            "pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "txt", "md", "zip");

    /** 允许作为头像的图片扩展名 */
    public static final Set<String> ALLOWED_IMAGE_EXTENSIONS = Set.of("jpg", "jpeg", "png", "gif", "webp");

    /** 扩展名合法性（字母数字，最长 10 位），二次防御异常输入 */
    private static final String EXT_PATTERN = "^[a-zA-Z0-9]{1,10}$";

    private StorageSupport() {
    }

    /**
     * 通用上传校验，返回规范化后的扩展名。
     *
     * @param allowed 允许的扩展名集合
     */
    public static String validate(MultipartFile file, Set<String> allowed) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException("上传文件不能为空");
        }
        String ext = extractExtension(file.getOriginalFilename());
        if (!allowed.contains(ext)) {
            throw new BusinessException("不支持的文件类型：" + ext);
        }
        String contentType = file.getContentType();
        if (contentType != null && contentType.toLowerCase(Locale.ROOT).contains("svg")) {
            throw new BusinessException("不支持的文件类型：svg");
        }
        return ext;
    }

    /** 提取并校验文件扩展名；无扩展名或格式非法时抛出业务异常 */
    public static String extractExtension(String originalFilename) {
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

    /** 原始文件名（仅展示用；做 cleanPath 防穿越，缺失时用 fallback） */
    public static String displayName(MultipartFile file, String fallback) {
        String name = file.getOriginalFilename();
        return StringUtils.cleanPath(StringUtils.hasText(name) ? name : fallback);
    }

    /** 按日期生成存储相对目录 yyyy/MM/dd */
    public static String datePath() {
        return LocalDate.now().toString().replace("-", "/");
    }
}
