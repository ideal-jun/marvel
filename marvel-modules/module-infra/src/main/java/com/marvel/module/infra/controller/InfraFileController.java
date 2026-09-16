package com.marvel.module.infra.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.marvel.common.annotation.Log;
import com.marvel.common.exception.BusinessException;
import com.marvel.common.result.R;
import com.marvel.module.infra.entity.SysFile;
import com.marvel.module.infra.mapper.SysFileMapper;
import com.marvel.module.infra.service.StorageService;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

/**
 * infra 域文件接口，路径前缀 /infra/**（与未来网关路由一致）。
 *
 * <p>下载走鉴权接口（{@code infra:file:download}）并强制 attachment；
 * {@code /uploads/**} 仍用于前端直接展示图片，但下载/管理以本控制器为准。
 */
@Slf4j
@RestController
@RequestMapping("/infra/file")
@RequiredArgsConstructor
public class InfraFileController {

    private final StorageService storageService;
    private final SysFileMapper fileMapper;

    /** 上传文件，返回可访问的 URL（类型/扩展名白名单校验见存储实现） */
    @SaCheckPermission("infra:file:upload")
    @PostMapping("/upload")
    public R<Map<String, String>> upload(@RequestParam("file") MultipartFile file) throws Exception {
        return R.ok(Map.of("url", storageService.upload(file)));
    }

    /** 文件分页列表 */
    @SaCheckPermission("infra:file:list")
    @GetMapping("/page")
    public R<IPage<SysFile>> page(@RequestParam(defaultValue = "1") long pageNum,
                                  @RequestParam(defaultValue = "10") long pageSize,
                                  @RequestParam(required = false) String fileName) {
        return R.ok(fileMapper.selectPage(new Page<>(pageNum, pageSize),
                new LambdaQueryWrapper<SysFile>()
                        .like(StringUtils.hasText(fileName), SysFile::getFileName, fileName)
                        .orderByDesc(SysFile::getFileId)));
    }

    /** 最近文件列表（供选择器/下拉场景） */
    @SaCheckPermission("infra:file:list")
    @GetMapping("/list")
    public R<List<SysFile>> list(@RequestParam(defaultValue = "50") int limit) {
        int size = Math.max(1, Math.min(limit, 200));
        return R.ok(fileMapper.selectList(new LambdaQueryWrapper<SysFile>()
                .orderByDesc(SysFile::getFileId)
                .last("LIMIT " + size)));
    }

    /** 下载：按存储根目录安全解析，强制 attachment 并携带原始文件名 */
    @SaCheckPermission("infra:file:download")
    @GetMapping("/download/{fileId}")
    public void download(@PathVariable Long fileId, HttpServletResponse response) throws IOException {
        SysFile file = fileMapper.selectById(fileId);
        if (file == null) {
            throw new BusinessException("文件不存在");
        }
        Resource resource = storageService.loadAsResource(file.getFilePath());
        String filename = StringUtils.hasText(file.getFileName()) ? file.getFileName() : "download";
        String encoded = URLEncoder.encode(filename, StandardCharsets.UTF_8).replace("+", "%20");
        response.setContentType(StringUtils.hasText(file.getContentType())
                ? file.getContentType() : "application/octet-stream");
        response.setHeader("Content-Disposition",
                "attachment; filename=\"" + encoded + "\"; filename*=UTF-8''" + encoded);
        response.setContentLengthLong(resource.contentLength());
        try (InputStream in = resource.getInputStream()) {
            in.transferTo(response.getOutputStream());
        }
    }

    /** 批量删除文件（记录 + 物理文件；物理文件缺失不影响记录清理） */
    @SaCheckPermission("infra:file:remove")
    @Log(title = "文件管理", businessType = Log.BusinessType.DELETE)
    @DeleteMapping("/{fileIds}")
    public R<Void> remove(@PathVariable List<Long> fileIds) {
        if (fileIds == null || fileIds.isEmpty()) {
            return R.ok();
        }
        for (Long fileId : fileIds) {
            SysFile file = fileMapper.selectById(fileId);
            if (file == null) {
                continue;
            }
            try {
                storageService.delete(file.getFilePath());
            } catch (Exception e) {
                log.warn("删除物理文件失败 id={} path={}: {}", fileId, file.getFilePath(), e.getMessage());
            }
            fileMapper.deleteById(fileId);
        }
        return R.ok();
    }
}
