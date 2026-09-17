package com.marvel.module.auth.controller;

import cn.dev33.satoken.exception.NotLoginException;
import cn.dev33.satoken.session.SaSession;
import cn.dev33.satoken.stp.StpLogic;
import cn.dev33.satoken.stp.StpUtil;
import com.marvel.api.system.SystemApi;
import com.marvel.api.system.dto.SysUserDTO;
import com.marvel.api.system.event.LoginRecordEvent;
import com.marvel.common.constant.Constants;
import com.marvel.common.exception.BusinessException;
import com.marvel.common.result.R;
import com.marvel.framework.web.ClientIpResolver;
import com.marvel.module.auth.dto.LoginBody;
import com.marvel.module.auth.service.CaptchaService;
import com.marvel.module.auth.service.LoginProtectService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AuthControllerTest {

    private static final String RAW_PASSWORD = "RightPass12";
    /** 全类共享一次 BCrypt 编码，避免每个用例重复付出 ~100ms 哈希成本 */
    private static final String PASSWORD_HASH = new BCryptPasswordEncoder().encode(RAW_PASSWORD);

    private SystemApi systemApi;
    private CaptchaService captchaService;
    private LoginProtectService loginProtectService;
    private ApplicationEventPublisher eventPublisher;
    private ClientIpResolver clientIpResolver;
    private StpLogic stpLogic;
    private StpLogic originalStpLogic;
    private SaSession session;
    private AuthController controller;

    @BeforeEach
    void setUp() {
        systemApi = mock(SystemApi.class);
        captchaService = mock(CaptchaService.class);
        loginProtectService = mock(LoginProtectService.class);
        eventPublisher = mock(ApplicationEventPublisher.class);
        clientIpResolver = mock(ClientIpResolver.class);
        // StpUtil 全静态委托到 stpLogic，注入 mock 后即可脱离 Web/Redis 环境单测；
        // loginType 必须打桩，否则 setStpLogic 内部 ConcurrentHashMap.put(null,…) 抛 NPE
        stpLogic = mock(StpLogic.class);
        when(stpLogic.getLoginType()).thenReturn("login");
        originalStpLogic = StpUtil.stpLogic;
        StpUtil.setStpLogic(stpLogic);
        session = new SaSession("test-session");
        when(stpLogic.getSession()).thenReturn(session);
        controller = new AuthController(systemApi, captchaService, loginProtectService,
                eventPublisher, clientIpResolver);
    }

    @AfterEach
    void tearDown() {
        StpUtil.setStpLogic(originalStpLogic);
    }

    private MockHttpServletRequest request() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0) Chrome/120.0 Safari/537.36");
        return request;
    }

    private LoginBody body(String username, String password) {
        LoginBody body = new LoginBody();
        body.setUsername(username);
        body.setPassword(password);
        return body;
    }

    private SysUserDTO user(String status) {
        SysUserDTO user = new SysUserDTO();
        user.setId(7L);
        user.setUsername("admin");
        user.setPassword(PASSWORD_HASH);
        user.setStatus(status);
        return user;
    }

    /** 登录默认前置条件：IP 解析、验证码关闭 */
    private void stubCommon(String ip) {
        when(clientIpResolver.resolve(any())).thenReturn(ip);
        when(systemApi.getConfigValue(Constants.CONFIG_CAPTCHA_ENABLED)).thenReturn("false");
    }

    @Test
    void captchaChecksRateLimitAndReturnsImage() {
        when(clientIpResolver.resolve(any())).thenReturn("9.9.9.9");
        when(captchaService.generateCaptcha()).thenReturn(Map.of("uuid", "u1", "img", "<svg/>"));

        R<Map<String, String>> result = controller.captcha(request());

        assertThat(result.getData()).containsEntry("uuid", "u1");
        verify(loginProtectService).checkCaptchaRate("9.9.9.9");
    }

    @Test
    void loginConfigDefaultsToCaptchaEnabledWhenParamMissing() {
        when(systemApi.getConfigValue(Constants.CONFIG_CAPTCHA_ENABLED)).thenReturn(null, " true ", "false");

        assertThat(controller.loginConfig().getData()).containsEntry("captchaEnabled", true);
        assertThat(controller.loginConfig().getData()).containsEntry("captchaEnabled", true);
        assertThat(controller.loginConfig().getData()).containsEntry("captchaEnabled", false);
    }

    @Test
    void loginSuccessIssuesTokenAndRecordsSessionAudit() {
        stubCommon("1.2.3.4");
        when(systemApi.getUserByUsername("admin")).thenReturn(user(Constants.STATUS_NORMAL));
        when(stpLogic.getTokenValue()).thenReturn("tk-123");
        MockHttpServletRequest request = request();

        R<Map<String, Object>> result = controller.login(body("admin", RAW_PASSWORD), request);

        assertThat(result.getData()).containsEntry("token", "tk-123");
        verify(stpLogic).login(7L);
        verify(loginProtectService).clearFailure("admin", "1.2.3.4");
        // 在线用户列表依赖这些会话审计字段
        assertThat(session.get("username")).isEqualTo("admin");
        assertThat(session.get("loginIp")).isEqualTo("1.2.3.4");
        assertThat(session.get("userAgent")).isEqualTo(request.getHeader("User-Agent"));
        assertThat(session.get("loginTime")).isNotNull();

        ArgumentCaptor<Object> event = ArgumentCaptor.forClass(Object.class);
        verify(eventPublisher).publishEvent(event.capture());
        LoginRecordEvent loginEvent = (LoginRecordEvent) event.getValue();
        assertThat(loginEvent.username()).isEqualTo("admin");
        assertThat(loginEvent.ipaddr()).isEqualTo("1.2.3.4");
        assertThat(loginEvent.status()).isEqualTo(Constants.STATUS_NORMAL);
        assertThat(loginEvent.browser()).isEqualTo("Chrome");
        assertThat(loginEvent.os()).isEqualTo("Windows");
    }

    @Test
    void loginWrongPasswordRecordsFailureAndSameMessageAsUnknownUser() {
        stubCommon("1.2.3.4");
        when(systemApi.getUserByUsername("admin")).thenReturn(user(Constants.STATUS_NORMAL));

        String wrongPasswordMsg = catchLoginMessage(body("admin", "WrongPass12"));

        verify(loginProtectService).recordFailure("admin", "1.2.3.4");
        verify(stpLogic, never()).login(any());

        when(systemApi.getUserByUsername("ghost")).thenReturn(null);
        String unknownUserMsg = catchLoginMessage(body("ghost", "Whatever12"));

        // 不区分「用户不存在」与「密码错误」，避免账号枚举
        assertThat(wrongPasswordMsg).isEqualTo(unknownUserMsg).isEqualTo("用户名或密码错误");
    }

    @Test
    void loginCaptchaRejectedBeforeUserLookup() {
        stubCommon("1.2.3.4");
        when(systemApi.getConfigValue(Constants.CONFIG_CAPTCHA_ENABLED)).thenReturn("true");
        when(captchaService.verify("uuid-1", "99")).thenReturn(false);

        assertThatThrownBy(() -> controller.login(body("admin", RAW_PASSWORD), request()))
                .isInstanceOf(BusinessException.class)
                .hasMessage("验证码错误或已过期");
        verify(systemApi, never()).getUserByUsername(any());
    }

    @Test
    void loginDisabledAccountRejected() {
        stubCommon("1.2.3.4");
        when(systemApi.getUserByUsername("admin")).thenReturn(user(Constants.STATUS_DISABLED));

        assertThatThrownBy(() -> controller.login(body("admin", RAW_PASSWORD), request()))
                .isInstanceOf(BusinessException.class)
                .hasMessage("账号已停用，请联系管理员");
        verify(stpLogic, never()).login(any());
    }

    @Test
    void loginLockedAccountShortCircuitsBeforeCaptchaAndPassword() {
        stubCommon("1.2.3.4");
        doThrow(new BusinessException("账号已锁定，请 10 分钟后再试"))
                .when(loginProtectService).checkLocked("admin", "1.2.3.4");

        assertThatThrownBy(() -> controller.login(body("admin", RAW_PASSWORD), request()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("锁定");
        verify(systemApi, never()).getUserByUsername(any());
    }

    @Test
    void getInfoMasksPasswordAndAggregatesRolesAndPermissions() {
        when(stpLogic.getLoginIdAsLong()).thenReturn(7L);
        SysUserDTO dbUser = user(Constants.STATUS_NORMAL);
        when(systemApi.getUserById(7L)).thenReturn(dbUser);
        when(systemApi.getRoleKeysByUserId(7L)).thenReturn(Set.of("admin"));
        when(systemApi.getPermissionsByUserId(7L)).thenReturn(Set.of("*:*:*"));

        R<Map<String, Object>> result = controller.getInfo();

        // 密码密文绝不外发
        assertThat(dbUser.getPassword()).isNull();
        assertThat(result.getData())
                .containsEntry("roles", Set.of("admin"))
                .containsEntry("permissions", Set.of("*:*:*"));
        assertThat(result.getData().get("user")).isSameAs(dbUser);
    }

    @Test
    void getInfoKicksGhostSessionWhenUserDeleted() {
        when(stpLogic.getLoginIdAsLong()).thenReturn(7L);
        when(systemApi.getUserById(7L)).thenReturn(null);
        // 注销幽灵会话后 checkLogin 按未登录处理（全局异常处理器转 401）
        doThrow(new NotLoginException("login", "token", NotLoginException.DEFAULT_MESSAGE))
                .when(stpLogic).checkLogin();

        assertThatThrownBy(controller::getInfo).isInstanceOf(NotLoginException.class);
        verify(stpLogic).logout();
    }

    @Test
    void getRoutersReturnsCurrentUserMenus() {
        when(stpLogic.getLoginIdAsLong()).thenReturn(7L);
        List<Object> menus = List.of(Map.of("menuName", "系统管理"));
        when(systemApi.getMenusByUserId(7L)).thenAnswer(inv -> menus);

        assertThat(controller.getRouters().getData()).isSameAs(menus);
    }

    @Test
    void logoutRecordsEventEvenForAnonymous() {
        when(clientIpResolver.resolve(any())).thenReturn("1.2.3.4");
        when(stpLogic.isLogin()).thenReturn(false);

        R<Void> result = controller.logout(request());

        assertThat(result.getCode()).isEqualTo(200);
        verify(stpLogic).logout();
        ArgumentCaptor<Object> event = ArgumentCaptor.forClass(Object.class);
        verify(eventPublisher).publishEvent(event.capture());
        LoginRecordEvent loginEvent = (LoginRecordEvent) event.getValue();
        assertThat(loginEvent.username()).isEqualTo("anonymous");
        assertThat(loginEvent.msg()).isEqualTo("退出成功");
    }

    /** 执行登录并返回业务异常消息 */
    private String catchLoginMessage(LoginBody body) {
        try {
            controller.login(body, request());
            throw new AssertionError("登录应抛出 BusinessException");
        } catch (BusinessException e) {
            return e.getMessage();
        }
    }
}
