package com.marvel.framework.web;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;

class ActuatorAccessFilterTest {

    private final ActuatorAccessFilter filter = new ActuatorAccessFilter();

    private MockHttpServletResponse run(String uri) throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(new MockHttpServletRequest("GET", uri), response, new MockFilterChain());
        return response;
    }

    @Test
    void healthProbesAreAllowed() throws Exception {
        assertThat(run("/actuator/health").getStatus()).isEqualTo(200);
        assertThat(run("/actuator/health/liveness").getStatus()).isEqualTo(200);
    }

    @Test
    void otherActuatorEndpointsReturn404() throws Exception {
        assertThat(run("/actuator").getStatus()).isEqualTo(404);
        assertThat(run("/actuator/info").getStatus()).isEqualTo(404);
        assertThat(run("/actuator/env").getStatus()).isEqualTo(404);
        assertThat(run("/actuator/beans").getStatus()).isEqualTo(404);
    }

    @Test
    void nonActuatorPathsPassThrough() throws Exception {
        assertThat(run("/system/user/page").getStatus()).isEqualTo(200);
    }
}
