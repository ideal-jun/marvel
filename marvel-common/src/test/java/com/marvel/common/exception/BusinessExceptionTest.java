package com.marvel.common.exception;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class BusinessExceptionTest {

    @Test
    void defaultCodeIsBusinessError400() {
        BusinessException e = new BusinessException("规则不满足");
        assertThat(e.getCode()).isEqualTo(400);
        assertThat(e.getMessage()).isEqualTo("规则不满足");
    }

    @Test
    void explicitCodePreserved() {
        assertThat(new BusinessException(409, "冲突").getCode()).isEqualTo(409);
    }
}
