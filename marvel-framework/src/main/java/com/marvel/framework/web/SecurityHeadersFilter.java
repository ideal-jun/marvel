package com.marvel.framework.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * 全局安全响应头过滤器（渗透扫描常见缺失项）：
 * 防止 MIME 嗅探、点击劫持、Referrer 泄露；对用户上传的静态资源追加沙箱 CSP，
 * 即使上传目录中出现可承载脚本的文件，直接访问时也不会执行脚本（存储型 XSS 防御）。
 */
@Component
public class SecurityHeadersFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        response.setHeader("X-Content-Type-Options", "nosniff");
        response.setHeader("X-Frame-Options", "DENY");
        response.setHeader("Referrer-Policy", "no-referrer");
        response.setHeader("Permissions-Policy", "geolocation=(), microphone=(), camera=()");
        String uri = request.getRequestURI();
        if (uri != null && uri.startsWith("/auth/")) {
            // 登录态与验证码响应禁止缓存，避免令牌/验证码被中间代理或浏览器落盘
            response.setHeader("Cache-Control", "no-store, no-cache, must-revalidate");
            response.setHeader("Pragma", "no-cache");
        }
        if (uri != null && uri.startsWith("/uploads/")) {
            // 上传目录可能包含 SVG 等可承载脚本的文件，强制沙箱并禁止加载任何外部资源
            response.setHeader("Content-Security-Policy", "default-src 'none'; style-src 'unsafe-inline'; sandbox");
        }
        filterChain.doFilter(request, response);
    }
}
