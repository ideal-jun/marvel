package com.marvel.common.security;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class PasswordPolicyTest {

    @ParameterizedTest
    @ValueSource(strings = {"Strong123", "Abcdefg1", "P@ssw0rd!", "a1b2c3d4e5", "Admin@123"})
    void validPasswordsAccepted(String password) {
        assertThat(PasswordPolicy.validate(password)).isNull();
        assertThat(PasswordPolicy.isValid(password)).isTrue();
    }

    @Test
    void nullRejected() {
        assertThat(PasswordPolicy.validate(null)).isEqualTo("密码不能为空");
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   "})
    void blankRejected(String password) {
        assertThat(PasswordPolicy.validate(password)).isEqualTo("密码不能为空");
    }

    @Test
    void tooShortRejected() {
        assertThat(PasswordPolicy.validate("Ab1")).contains("8-32");
    }

    @Test
    void tooLongRejected() {
        assertThat(PasswordPolicy.validate("a".repeat(33))).contains("8-32");
    }

    @Test
    void whitespaceRejected() {
        assertThat(PasswordPolicy.validate("Abc 12345")).contains("空白");
    }

    @Test
    void lettersOnlyRejected() {
        assertThat(PasswordPolicy.validate("abcdefgh")).contains("字母和数字");
    }

    @Test
    void digitsOnlyRejected() {
        assertThat(PasswordPolicy.validate("12345678")).contains("字母和数字");
    }

    @Test
    void boundaryLength8Accepted() {
        assertThat(PasswordPolicy.isValid("Abcdefg1")).isTrue();
    }

    @Test
    void boundaryLength32Accepted() {
        assertThat(PasswordPolicy.isValid("A1" + "b".repeat(30))).isTrue();
    }
}
