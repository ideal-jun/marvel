package com.marvel.module.infra.jobs;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class JobInvokeTargetTest {

    @ParameterizedTest
    @ValueSource(strings = {"sampleJob.run", "sysJobServiceImpl.refresh", "a1.b2", " sampleJob.run "})
    void validFormatsAccepted(String target) {
        assertThat(JobInvokeTarget.isValidFormat(target)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "java.lang.Runtime.exec",
            "java.lang.Runtime.getRuntime().exec('calc')",
            "(new java.lang.ProcessBuilder()).start",
            "bean.method('x')",
            "bean..method",
            "bean.method()",
            ".method",
            "bean.",
            "bean method",
            "bean/method",
            "bean.method;drop",
            "bean\u0000.method"
    })
    void dangerousOrMalformedRejected(String target) {
        assertThat(JobInvokeTarget.isValidFormat(target)).isFalse();
    }

    @Test
    void nullRejected() {
        assertThat(JobInvokeTarget.isValidFormat(null)).isFalse();
    }

    @Test
    void beanAndMethodExtraction() {
        assertThat(JobInvokeTarget.beanName(" sampleJob.run ")).isEqualTo("sampleJob");
        assertThat(JobInvokeTarget.methodName(" sampleJob.run ")).isEqualTo("run");
    }
}
