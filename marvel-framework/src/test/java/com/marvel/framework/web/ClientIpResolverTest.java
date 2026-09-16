package com.marvel.framework.web;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;

class ClientIpResolverTest {

    private ClientIpResolver resolver(boolean trustForwardedHeaders) {
        ClientIpResolver resolver = new ClientIpResolver();
        ReflectionTestUtils.setField(resolver, "trustForwardedHeaders", trustForwardedHeaders);
        return resolver;
    }

    @Test
    void defaultIgnoresSpoofedForwardedHeaders() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("10.0.0.1");
        request.addHeader("X-Forwarded-For", "9.9.9.9, 8.8.8.8");
        request.addHeader("X-Real-IP", "7.7.7.7");

        assertThat(resolver(false).resolve(request)).isEqualTo("10.0.0.1");
    }

    @Test
    void usesFirstXffSegmentWhenTrusted() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("10.0.0.1");
        request.addHeader("X-Forwarded-For", "9.9.9.9, 8.8.8.8");

        assertThat(resolver(true).resolve(request)).isEqualTo("9.9.9.9");
    }

    @Test
    void fallsBackToRealIpWhenTrusted() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("10.0.0.1");
        request.addHeader("X-Real-IP", "7.7.7.7");

        assertThat(resolver(true).resolve(request)).isEqualTo("7.7.7.7");
    }

    @Test
    void nullRequestReturnsEmpty() {
        assertThat(resolver(false).resolve(null)).isEmpty();
    }
}
