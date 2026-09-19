package com.marvel.common.validation;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ContactPolicyTest {

    @Test
    void blankValuesAreOptional() {
        assertThat(ContactPolicy.validatePhone(null)).isNull();
        assertThat(ContactPolicy.validatePhone("  ")).isNull();
        assertThat(ContactPolicy.validateEmail(null)).isNull();
        assertThat(ContactPolicy.validateEmail("")).isNull();
    }

    @Test
    void validPhonePasses() {
        assertThat(ContactPolicy.validatePhone("13800000000")).isNull();
        assertThat(ContactPolicy.validatePhone("19912345678")).isNull();
    }

    @Test
    void invalidPhoneRejected() {
        assertThat(ContactPolicy.validatePhone("1380000000")).isEqualTo("手机号格式不正确");
        assertThat(ContactPolicy.validatePhone("12800000000")).isEqualTo("手机号格式不正确");
        assertThat(ContactPolicy.validatePhone("1380000000a")).isEqualTo("手机号格式不正确");
        assertThat(ContactPolicy.validatePhone("+8613800000000")).isEqualTo("手机号格式不正确");
    }

    @Test
    void validEmailPasses() {
        assertThat(ContactPolicy.validateEmail("ry@163.com")).isNull();
        assertThat(ContactPolicy.validateEmail("a.b+tag@sub.example.co.uk")).isNull();
    }

    @Test
    void invalidEmailRejected() {
        assertThat(ContactPolicy.validateEmail("ry@163")).isEqualTo("邮箱格式不正确");
        assertThat(ContactPolicy.validateEmail("ry.163.com")).isEqualTo("邮箱格式不正确");
        assertThat(ContactPolicy.validateEmail("ry@.com")).isEqualTo("邮箱格式不正确");
        assertThat(ContactPolicy.validateEmail(" ry@163.com ")).isNull();
    }

    @Test
    void tooLongEmailRejected() {
        String longLocal = "a".repeat(ContactPolicy.MAX_EMAIL_LENGTH) + "@163.com";
        assertThat(ContactPolicy.validateEmail(longLocal)).startsWith("邮箱长度不能超过");
    }
}
