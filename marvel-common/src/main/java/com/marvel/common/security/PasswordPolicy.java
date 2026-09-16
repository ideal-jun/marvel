package com.marvel.common.security;

/**
 * 统一的密码复杂度策略。
 *
 * <p>抽成无状态纯函数，便于单元测试，并保证「创建用户 / 重置密码 / 修改密码」三处
 * 使用完全一致的规则。规则：长度 8-32 位、不含空白字符、且同时包含字母与数字。
 */
public final class PasswordPolicy {

    public static final int MIN_LENGTH = 8;
    public static final int MAX_LENGTH = 32;

    private PasswordPolicy() {
    }

    /**
     * 校验密码。
     *
     * @return 通过返回 {@code null}，否则返回可直接展示给用户的错误信息
     */
    public static String validate(String password) {
        if (password == null || password.isBlank()) {
            return "密码不能为空";
        }
        if (password.length() < MIN_LENGTH || password.length() > MAX_LENGTH) {
            return "密码长度需为 " + MIN_LENGTH + "-" + MAX_LENGTH + " 位";
        }
        if (password.chars().anyMatch(Character::isWhitespace)) {
            return "密码不能包含空白字符";
        }
        boolean hasLetter = password.chars().anyMatch(Character::isLetter);
        boolean hasDigit = password.chars().anyMatch(Character::isDigit);
        if (!hasLetter || !hasDigit) {
            return "密码必须同时包含字母和数字";
        }
        return null;
    }

    /** 是否满足密码策略 */
    public static boolean isValid(String password) {
        return validate(password) == null;
    }
}
