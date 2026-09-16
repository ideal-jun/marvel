package com.marvel.framework.web;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 客户端真实 IP 解析。
 *
 * <p>安全约束：X-Forwarded-For / X-Real-IP 由客户端完全可控。若默认信任，攻击者可
 * 伪造 IP 绕过「用户名+IP」维度的登录防爆破锁定并污染审计日志。因此默认只使用
 * {@link HttpServletRequest#getRemoteAddr()}，仅当部署在可信反向代理之后且显式开启
 * {@code marvel.security.trust-forwarded-headers=true} 时才解析代理头。
 */
@Component
public class ClientIpResolver {

    @Value("${marvel.security.trust-forwarded-headers:false}")
    private boolean trustForwardedHeaders;

    public String resolve(HttpServletRequest request) {
        if (request == null) {
            return "";
        }
        if (trustForwardedHeaders) {
            String xff = request.getHeader("X-Forwarded-For");
            if (xff != null && !xff.isBlank()) {
                return xff.split(",")[0].trim();
            }
            String realIp = request.getHeader("X-Real-IP");
            if (realIp != null && !realIp.isBlank()) {
                return realIp.trim();
            }
        }
        return request.getRemoteAddr();
    }
}
