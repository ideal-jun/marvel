package com.marvel.framework.aspect;

import cn.dev33.satoken.stp.StpUtil;
import com.marvel.api.system.event.OperLogEvent;
import com.marvel.common.annotation.Log;
import com.marvel.framework.web.ClientIpResolver;
import jakarta.servlet.http.HttpServletRequest;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.multipart.MultipartFile;
import tools.jackson.databind.json.JsonMapper;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 操作日志切面：拦截 {@link Log} 注解方法，环绕执行并发布操作日志事件。
 * 成功与异常均记录（异常记录后原样抛出）；参数序列化前做敏感字段脱敏与长度截断，
 * 避免密码等机密信息落库、超大参数撑爆日志表。
 */
@Aspect
@Component
public class OperLogAspect {

    private static final Logger log = LoggerFactory.getLogger(OperLogAspect.class);

    /** 参数最大保留长度（TEXT 列足够，但控制在合理范围） */
    private static final int MAX_PARAM_LENGTH = 2000;

    /** 需要脱敏的敏感字段（不区分大小写，命中即置为 ***） */
    private static final String[] SENSITIVE_KEYS = {"password", "oldpassword", "newpassword", "confirmpassword"};

    private final ApplicationEventPublisher eventPublisher;
    private final ClientIpResolver clientIpResolver;

    /** Jackson 3（SB4 默认）：java.time 内置支持，日期输出 ISO 字符串 */
    private final JsonMapper jsonMapper = JsonMapper.builder().build();

    public OperLogAspect(ApplicationEventPublisher eventPublisher, ClientIpResolver clientIpResolver) {
        this.eventPublisher = eventPublisher;
        this.clientIpResolver = clientIpResolver;
    }

    @Around("@annotation(logAnnotation)")
    public Object around(ProceedingJoinPoint joinPoint, Log logAnnotation) throws Throwable {
        long start = System.currentTimeMillis();
        try {
            Object result = joinPoint.proceed();
            publish(joinPoint, logAnnotation, "0", null);
            return result;
        } catch (Throwable e) {
            publish(joinPoint, logAnnotation, "1", truncate(e.getMessage(), 2000));
            throw e;
        } finally {
            // 耗时仅用于排查参考，不打断业务
            if (log.isDebugEnabled()) {
                log.debug("[operLog] {} cost {}ms", logAnnotation.title(), System.currentTimeMillis() - start);
            }
        }
    }

    private void publish(ProceedingJoinPoint joinPoint, Log ann, String status, String errorMsg) {
        try {
            MethodSignature signature = (MethodSignature) joinPoint.getSignature();
            HttpServletRequest request = currentRequest();

            eventPublisher.publishEvent(new OperLogEvent(
                    ann.title(),
                    ann.businessType() + " " + signature.getDeclaringTypeName() + "." + signature.getName(),
                    request != null ? request.getMethod() : "",
                    request != null ? request.getRequestURI() : "",
                    serializeArgs(joinPoint.getArgs()),
                    currentUsername(),
                    request != null ? clientIpResolver.resolve(request) : "",
                    status,
                    errorMsg
            ));
        } catch (Exception e) {
            // 日志记录自身绝不影响业务
            log.warn("操作日志发布失败: {}", e.getMessage());
        }
    }

    /** 序列化方法参数：过滤 HttpServletRequest 等不可序列化对象，敏感字段脱敏后截断 */
    private String serializeArgs(Object[] args) {
        if (args == null || args.length == 0) {
            return "";
        }
        List<Object> plain = new ArrayList<>();
        for (Object arg : args) {
            if (arg == null
                    || arg instanceof HttpServletRequest
                    || arg instanceof MultipartFile
                    || arg instanceof byte[]) {
                continue;
            }
            plain.add(arg);
        }
        if (plain.isEmpty()) {
            return "";
        }
        try {
            Object sanitized = plain.size() == 1 ? sanitizeValue(plain.get(0)) : plain.stream().map(this::sanitizeValue).toList();
            return truncate(jsonMapper.writeValueAsString(sanitized), MAX_PARAM_LENGTH);
        } catch (Exception e) {
            // 兜底：序列化失败也不能影响业务，退化为类型描述
            return truncate(plain.stream().map(a -> a.getClass().getSimpleName()).toList().toString(), 200);
        }
    }

    /**
     * 入口脱敏：JavaBean（DTO/实体）先转 Map 再递归处理，
     * 保证 password 等敏感字段在任意层级、任意载体中都会被打码。
     */
    private Object sanitizeValue(Object value) {
        if (value instanceof Map || value instanceof List) {
            return sanitize(value);
        }
        if (value == null || value instanceof String || value instanceof Number || value instanceof Boolean) {
            return value;
        }
        try {
            Map<?, ?> asMap = jsonMapper.convertValue(value, Map.class);
            return sanitize(asMap);
        } catch (Exception e) {
            // 无法转 Map 的类型（枚举、日期等），不含可脱敏结构，直接返回
            return value;
        }
    }

    /**
     * 递归脱敏：Map 命中敏感 key 的值置为 ***，List 逐项递归。
     */
    @SuppressWarnings("unchecked")
    private Object sanitize(Object value) {
        if (value instanceof Map<?, ?> map) {
            Map<Object, Object> out = new java.util.LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                String key = String.valueOf(entry.getKey());
                out.put(key, isSensitive(key) ? "***" : sanitizeValue(entry.getValue()));
            }
            return out;
        }
        if (value instanceof List<?> list) {
            return list.stream().map(this::sanitizeValue).toList();
        }
        return value;
    }

    private boolean isSensitive(String key) {
        String lower = key.toLowerCase();
        for (String s : SENSITIVE_KEYS) {
            if (lower.contains(s)) {
                return true;
            }
        }
        return false;
    }

    private String currentUsername() {
        try {
            return StpUtil.isLogin() ? String.valueOf(StpUtil.getLoginId()) : "anonymous";
        } catch (Exception e) {
            return "anonymous";
        }
    }

    private HttpServletRequest currentRequest() {
        var attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        return attrs != null ? attrs.getRequest() : null;
    }

    private String truncate(String s, int max) {
        if (s == null) {
            return null;
        }
        return s.length() <= max ? s : s.substring(0, max);
    }
}
