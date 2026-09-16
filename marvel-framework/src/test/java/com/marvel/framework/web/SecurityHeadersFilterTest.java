package com.marvel.framework.web;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;

class SecurityHeadersFilterTest {

    private final SecurityHeadersFilter filter = new SecurityHeadersFilter();

    private MockHttpServletResponse run(String uri) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", uri);
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, new MockFilterChain());
        return response;
    }

    @Test
    void baseSecurityHeadersAppliedToApi() throws Exception {
        MockHttpServletResponse response = run("/system/user/page");

        assertThat(response.getHeader("X-Content-Type-Options")).isEqualTo("nosniff");
        assertThat(response.getHeader("X-Frame-Options")).isEqualTo("DENY");
        assertThat(response.getHeader("Referrer-Policy")).isEqualTo("no-referrer");
        assertThat(response.getHeader("Permissions-Policy")).isNotNull();
    }

    @Test
    void authResponsesAreNotCacheable() throws Exception {
        MockHttpServletResponse response = run("/auth/captcha");

        assertThat(response.getHeader("Cache-Control")).contains("no-store");
    }

    @Test
    void uploadResponsesGetSandboxCsp() throws Exception {
        MockHttpServletResponse response = run("/uploads/2026/09/16/a.svg");

        assertThat(response.getHeader("Content-Security-Policy")).contains("sandbox");
    }
}
