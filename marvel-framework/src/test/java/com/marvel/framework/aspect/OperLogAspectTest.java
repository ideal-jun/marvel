package com.marvel.framework.aspect;

import cn.dev33.satoken.stp.StpLogic;
import cn.dev33.satoken.stp.StpUtil;
import com.marvel.api.system.event.OperLogEvent;
import com.marvel.common.annotation.Log;
import com.marvel.framework.web.ClientIpResolver;
import jakarta.servlet.http.HttpServletRequest;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OperLogAspectTest {

    /** 与用户管理 DTO 同构的测试载荷：password 字段必须被脱敏 */
    static class UserPayload {
        private final String username;
        private final String password;

        UserPayload(String username, String password) {
            this.username = username;
            this.password = password;
        }

        public String getUsername() {
            return username;
        }

        public String getPassword() {
            return password;
        }
    }

    /** 供反射获取注解实例的桩方法 */
    @Log(title = "用户管理", businessType = Log.BusinessType.UPDATE)
    void annotatedMethod() {
    }

    private ApplicationEventPublisher eventPublisher;
    private ClientIpResolver clientIpResolver;
    private ProceedingJoinPoint joinPoint;
    private MethodSignature signature;
    private StpLogic stpLogic;
    private StpLogic originalStpLogic;
    private Log logAnnotation;
    private OperLogAspect aspect;

    @BeforeEach
    void setUp() throws Exception {
        eventPublisher = mock(ApplicationEventPublisher.class);
        clientIpResolver = mock(ClientIpResolver.class);
        joinPoint = mock(ProceedingJoinPoint.class);
        signature = mock(MethodSignature.class);
        when(joinPoint.getSignature()).thenReturn(signature);
        when(signature.getDeclaringTypeName()).thenReturn("com.marvel.SysUserController");
        when(signature.getName()).thenReturn("updateUser");
        stpLogic = mock(StpLogic.class);
        when(stpLogic.getLoginType()).thenReturn("login");
        when(stpLogic.isLogin()).thenReturn(false);
        originalStpLogic = StpUtil.stpLogic;
        StpUtil.setStpLogic(stpLogic);
        logAnnotation = getClass().getDeclaredMethod("annotatedMethod").getAnnotation(Log.class);
        aspect = new OperLogAspect(eventPublisher, clientIpResolver);
    }

    @AfterEach
    void tearDown() {
        StpUtil.setStpLogic(originalStpLogic);
        RequestContextHolder.resetRequestAttributes();
    }

    private void bindRequest(String method, String uri, String ip) {
        MockHttpServletRequest request = new MockHttpServletRequest(method, uri);
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        when(clientIpResolver.resolve(any(HttpServletRequest.class))).thenReturn(ip);
    }

    private OperLogEvent captureSingleEvent() {
        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
        verify(eventPublisher).publishEvent(captor.capture());
        return (OperLogEvent) captor.getValue();
    }

    @Test
    void successPublishesEventWithStatusNormal() throws Throwable {
        bindRequest("PUT", "/system/user", "1.2.3.4");
        when(joinPoint.getArgs()).thenReturn(new Object[]{new UserPayload("bob", "secret")});
        when(joinPoint.proceed()).thenReturn("ok");

        Object result = aspect.around(joinPoint, logAnnotation);

        assertThat(result).isEqualTo("ok");
        OperLogEvent event = captureSingleEvent();
        assertThat(event.title()).isEqualTo("用户管理");
        assertThat(event.method()).contains("UPDATE").contains("SysUserController.updateUser");
        assertThat(event.requestMethod()).isEqualTo("PUT");
        assertThat(event.operUrl()).isEqualTo("/system/user");
        assertThat(event.operIp()).isEqualTo("1.2.3.4");
        assertThat(event.status()).isEqualTo("0");
        assertThat(event.errorMsg()).isNull();
    }

    @Test
    void failurePublishesEventThenRethrows() throws Throwable {
        bindRequest("PUT", "/system/user", "1.2.3.4");
        when(joinPoint.getArgs()).thenReturn(new Object[0]);
        when(joinPoint.proceed()).thenThrow(new IllegalStateException("boom"));

        assertThatThrownBy(() -> aspect.around(joinPoint, logAnnotation))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("boom");

        OperLogEvent event = captureSingleEvent();
        assertThat(event.status()).isEqualTo("1");
        assertThat(event.errorMsg()).isEqualTo("boom");
    }

    @Test
    void passwordFieldsAreMaskedInOperParam() throws Throwable {
        bindRequest("PUT", "/system/user", "1.2.3.4");
        when(joinPoint.getArgs()).thenReturn(new Object[]{new UserPayload("bob", "secret123")});
        when(joinPoint.proceed()).thenReturn("ok");

        aspect.around(joinPoint, logAnnotation);

        OperLogEvent event = captureSingleEvent();
        assertThat(event.operParam()).contains("***").doesNotContain("secret123");
    }

    @Test
    void nestedSensitiveKeysAreMaskedRecursively() throws Throwable {
        bindRequest("PUT", "/system/user", "1.2.3.4");
        when(joinPoint.getArgs()).thenReturn(new Object[]{
                Map.of("user", Map.of("newPassword", "n-pwd", "username", "bob"))});
        when(joinPoint.proceed()).thenReturn("ok");

        aspect.around(joinPoint, logAnnotation);

        OperLogEvent event = captureSingleEvent();
        assertThat(event.operParam()).contains("***").doesNotContain("n-pwd").contains("bob");
    }

    @Test
    void servletAndBinaryArgsAreSkipped() throws Throwable {
        bindRequest("PUT", "/system/user", "1.2.3.4");
        when(joinPoint.getArgs()).thenReturn(new Object[]{
                new MockHttpServletRequest(), new byte[]{1, 2}, "plain"});
        when(joinPoint.proceed()).thenReturn("ok");

        aspect.around(joinPoint, logAnnotation);

        OperLogEvent event = captureSingleEvent();
        assertThat(event.operParam()).isEqualTo("\"plain\"");
    }

    @Test
    void oversizedParamIsTruncated() throws Throwable {
        bindRequest("PUT", "/system/user", "1.2.3.4");
        when(joinPoint.getArgs()).thenReturn(new Object[]{Map.of("data", "x".repeat(3000))});
        when(joinPoint.proceed()).thenReturn("ok");

        aspect.around(joinPoint, logAnnotation);

        OperLogEvent event = captureSingleEvent();
        assertThat(event.operParam()).hasSize(2000);
    }

    @Test
    void withoutRequestContextStillPublishesWithEmptyRequestFields() throws Throwable {
        when(joinPoint.getArgs()).thenReturn(new Object[]{List.of("a")});
        when(joinPoint.proceed()).thenReturn("ok");

        aspect.around(joinPoint, logAnnotation);

        OperLogEvent event = captureSingleEvent();
        assertThat(event.requestMethod()).isEmpty();
        assertThat(event.operUrl()).isEmpty();
        assertThat(event.operIp()).isEmpty();
        // 未登录场景记录为匿名
        assertThat(event.operUser()).isEqualTo("anonymous");
    }

    @Test
    void loggedInUserRecordedAsOperUser() throws Throwable {
        bindRequest("PUT", "/system/user", "1.2.3.4");
        when(stpLogic.isLogin()).thenReturn(true);
        when(stpLogic.getLoginId()).thenReturn(7L);
        when(joinPoint.getArgs()).thenReturn(new Object[0]);
        when(joinPoint.proceed()).thenReturn("ok");

        aspect.around(joinPoint, logAnnotation);

        assertThat(captureSingleEvent().operUser()).isEqualTo("7");
    }
}
