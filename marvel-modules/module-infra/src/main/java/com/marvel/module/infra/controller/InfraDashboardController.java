package com.marvel.module.infra.controller;

import com.marvel.common.result.R;
import com.marvel.module.infra.mapper.SysJobLogMapper;
import com.marvel.module.infra.mapper.SysJobMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 首页看板 infra 域统计（登录即可访问）。
 *
 * <p>与 {@code /system/dashboard/**} 分属不同模块，保持未来拆分服务时的统计归属。
 */
@RestController
@RequestMapping("/infra/dashboard")
@RequiredArgsConstructor
public class InfraDashboardController {

    private final SysJobMapper jobMapper;
    private final SysJobLogMapper jobLogMapper;

    /** 任务统计：任务总数、启用数、执行成功/失败次数 */
    @GetMapping("/job-stats")
    public R<Map<String, Object>> jobStats() {
        long success = 0L;
        long fail = 0L;
        for (Map<String, Object> row : jobLogMapper.countByStatus()) {
            long count = row.get("c") instanceof Number n ? n.longValue() : 0L;
            if ("0".equals(String.valueOf(row.get("s")))) {
                success += count;
            } else {
                fail += count;
            }
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("total", jobMapper.selectCount(null));
        data.put("enabled", jobMapper.countEnabled());
        data.put("success", success);
        data.put("fail", fail);
        return R.ok(data);
    }
}
