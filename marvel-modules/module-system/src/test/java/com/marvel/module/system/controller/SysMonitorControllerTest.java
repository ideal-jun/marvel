package com.marvel.module.system.controller;

import com.marvel.common.result.R;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.RedisConnectionFactory;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class SysMonitorControllerTest {

    private final SysMonitorController controller =
            new SysMonitorController(mock(RedisConnectionFactory.class));

    @SuppressWarnings("unchecked")
    @Test
    void serverReturnsAllSections() {
        R<Map<String, Object>> result = controller.server();

        assertThat(result.getCode()).isEqualTo(200);
        assertThat(result.getData()).containsKeys("sys", "cpu", "memory", "jvm", "disk");
        Map<String, Object> jvm = (Map<String, Object>) result.getData().get("jvm");
        assertThat(jvm).containsKeys("javaVersion", "heapUsed", "heapMax", "threadCount");
    }

    @Test
    void humanFormatsBytes() {
        assertThat(SysMonitorController.human(512)).isEqualTo("512 B");
        assertThat(SysMonitorController.human(1024)).isEqualTo("1.00 KB");
        assertThat(SysMonitorController.human(1536)).isEqualTo("1.50 KB");
        assertThat(SysMonitorController.human(1024L * 1024 * 1024)).isEqualTo("1.00 GB");
    }
}
