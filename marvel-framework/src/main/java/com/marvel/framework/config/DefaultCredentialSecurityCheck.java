package com.marvel.framework.config;

import com.marvel.api.system.SystemApi;
import com.marvel.api.system.dto.SysUserDTO;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * 启动安全检查：检测内置超级管理员是否仍在使用演示默认口令 admin123。
 *
 * <p>默认口令是渗透测试固定高危项。本类在启动后主动告警，提示运维在首次登录后
 * 立即修改；真正的强口令约束由服务端密码策略与首次部署流程共同保证。
 */
@Component
@RequiredArgsConstructor
public class DefaultCredentialSecurityCheck implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DefaultCredentialSecurityCheck.class);
    private static final String DEFAULT_ADMIN_USERNAME = "admin";
    private static final String DEFAULT_ADMIN_PASSWORD = "admin123";

    private final SystemApi systemApi;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    @Override
    public void run(ApplicationArguments args) {
        try {
            SysUserDTO admin = systemApi.getUserByUsername(DEFAULT_ADMIN_USERNAME);
            if (admin != null && admin.getPassword() != null
                    && passwordEncoder.matches(DEFAULT_ADMIN_PASSWORD, admin.getPassword())) {
                log.warn("【安全告警】内置超级管理员仍在使用演示默认口令 {}，请在首次登录后立即修改密码！",
                        DEFAULT_ADMIN_PASSWORD);
            }
        } catch (Exception e) {
            // 启动期数据库可能尚未就绪，检查失败不影响应用启动
            log.debug("默认口令安全检查跳过: {}", e.getMessage());
        }
    }
}
