package com.marvel.framework.config;

import cn.dev33.satoken.interceptor.SaInterceptor;
import cn.dev33.satoken.router.SaRouter;
import cn.dev33.satoken.stp.StpUtil;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Sa-Token 路由鉴权与 Web 全局配置。
 *
 * <p>安全约定：
 * <ul>
 *   <li>除登录/验证码等公开接口外，所有请求必须持有有效 Sa-Token；</li>
 *   <li>细粒度的按钮级权限由各 Controller 上的 {@code @SaCheckPermission} 控制；</li>
 *   <li>CORS 白名单从配置 marvel.security.allowed-origins 读取，禁止生产环境使用 "*"，
 *       避免跨域凭据泄露（渗透扫描高危项）。</li>
 * </ul>
 */
@Configuration
public class SaTokenConfig implements WebMvcConfigurer {

    /** 允许跨域的来源列表，逗号分隔；为空时表示同源部署，不开启 CORS */
    @Value("${marvel.security.allowed-origins:}")
    private String allowedOrigins;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new SaInterceptor(handle ->
                        SaRouter.match("/**")
                                // 公开接口：登录、验证码（登出未登录时为幂等空操作）
                                .notMatch("/auth/login", "/auth/captcha", "/auth/logout")
                                .notMatch("/error", "/favicon.ico", "/uploads/**")
                                // 健康检查探针公开（仅返回 UP/DOWN，不含细节）；/actuator/info 等仍需登录
                                .notMatch("/actuator/health", "/actuator/health/**")
                                .check(r -> StpUtil.checkLogin())))
                .addPathPatterns("/**");
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        if (!StringUtils.hasText(allowedOrigins)) {
            // 未配置白名单则不启用 CORS（前后端同域或由网关/Nginx 统一处理）
            return;
        }
        for (String origin : allowedOrigins.split(",")) {
            if ("*".equals(origin.trim())) {
                throw new IllegalStateException(
                        "marvel.security.allowed-origins 不允许配置为 \"*\"：与 allowCredentials 冲突且存在凭据泄露风险");
            }
        }
        registry.addMapping("/**")
                .allowedOrigins(allowedOrigins.split(","))
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .allowCredentials(true)
                .maxAge(3600);
    }
}
