package com.marvel.module.infra.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import cn.dev33.satoken.annotation.SaCheckPermission;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.marvel.common.result.R;
import com.marvel.module.infra.entity.SysFile;
import com.marvel.module.infra.mapper.SysFileMapper;
import com.marvel.module.infra.service.StorageService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

/**
 * infra 域文件接口，路径前缀 /infra/**（与未来网关路由一致）。
 */
@RestController
@RequestMapping("/infra/file")
@RequiredArgsConstructor
public class InfraFileController {

    private final StorageService storageService;
    private final SysFileMapper fileMapper;

    /** 上传文件，返回可访问的 URL（类型/扩展名白名单校验见存储实现） */
    @SaCheckLogin
    @PostMapping("/upload")
    public R<Map<String, String>> upload(@RequestParam("file") MultipartFile file) throws Exception {
        return R.ok(Map.of("url", storageService.upload(file)));
    }

    /** 最近上传文件列表（文件治理/审计用） */
    @SaCheckPermission("infra:file:list")
    @GetMapping("/list")
    public R<List<SysFile>> list(@RequestParam(defaultValue = "50") int limit) {
        int size = Math.max(1, Math.min(limit, 200));
        return R.ok(fileMapper.selectList(new LambdaQueryWrapper<SysFile>()
                .orderByDesc(SysFile::getFileId)
                .last("LIMIT " + size)));
    }
}
