package com.marvel.framework.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Actuator 访问收口：仅放行健康检查探针（{@code /actuator/health} 及其子路径），
 * 其余 {@code /actuator/**} 一律返回 404，避免通过 Actuator 泄露内部信息或探测端点存在性。
 *
 * <p>Actuator 端点由独立的 HandlerMapping 处理，Sa-Token 的 MVC 拦截器不一定覆盖，
 * 因此需要过滤器这一层兜底。
 */
@Component
public class ActuatorAccessFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String uri = request.getRequestURI();
        if (uri != null && (uri.equals("/actuator") || uri.startsWith("/actuator/"))) {
            boolean healthProbe = uri.equals("/actuator/health") || uri.startsWith("/actuator/health/");
            if (!healthProbe) {
                response.setStatus(HttpServletResponse.SC_NOT_FOUND);
                return;
            }
        }
        filterChain.doFilter(request, response);
    }
}
