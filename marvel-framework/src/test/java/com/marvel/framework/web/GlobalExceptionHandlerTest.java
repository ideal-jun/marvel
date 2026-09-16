package com.marvel.framework.web;

import com.marvel.common.exception.BusinessException;
import com.marvel.common.result.R;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerTest {

    @Test
    void businessExceptionMapsToHttp400() throws Exception {
        GlobalExceptionHandler handler = new GlobalExceptionHandler();

        R<Void> result = handler.handleBusiness(new BusinessException("业务失败"));

        assertThat(result.getCode()).isEqualTo(400);
        assertThat(result.getMsg()).isEqualTo("业务失败");
        ResponseStatus status = GlobalExceptionHandler.class
                .getMethod("handleBusiness", BusinessException.class)
                .getAnnotation(ResponseStatus.class);
        assertThat(status).isNotNull();
        assertThat(status.value()).isEqualTo(HttpStatus.BAD_REQUEST);
    }
}
