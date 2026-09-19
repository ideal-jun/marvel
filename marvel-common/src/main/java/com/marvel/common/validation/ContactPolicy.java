package com.marvel.common.validation;

import java.util.regex.Pattern;

/**
 * 联系方式格式校验（手机号 / 邮箱）。
 *
 * <p>抽成无状态纯函数，便于单元测试，并保证「个人资料 / 新增用户 / 编辑用户 / Excel 导入」
 * 等所有写入路径使用完全一致的规则。空白值视为未填写（可选字段），返回 {@code null}。
 */
public final class ContactPolicy {

    /** 中国大陆手机号：1 开头、第二位 3-9、共 11 位 */
    private static final Pattern PHONE = Pattern.compile("^1[3-9]\\d{9}$");

    /** 邮箱：本地部分允许常见字符，域名可多级，顶级域 2-63 个字母 */
    private static final Pattern EMAIL =
            Pattern.compile("^[A-Za-z0-9._%+-]+@[A-Za-z0-9-]+(\\.[A-Za-z0-9-]+)*\\.[A-Za-z]{2,63}$");

    public static final int MAX_EMAIL_LENGTH = 64;

    private ContactPolicy() {
    }

    /**
     * 校验手机号。
     *
     * @return 通过返回 {@code null}，否则返回可直接展示给用户的错误信息
     */
    public static String validatePhone(String phone) {
        if (phone == null || phone.isBlank()) {
            return null;
        }
        if (!PHONE.matcher(phone.trim()).matches()) {
            return "手机号格式不正确";
        }
        return null;
    }

    /**
     * 校验邮箱。
     *
     * @return 通过返回 {@code null}，否则返回可直接展示给用户的错误信息
     */
    public static String validateEmail(String email) {
        if (email == null || email.isBlank()) {
            return null;
        }
        String value = email.trim();
        if (value.length() > MAX_EMAIL_LENGTH) {
            return "邮箱长度不能超过 " + MAX_EMAIL_LENGTH + " 位";
        }
        if (!EMAIL.matcher(value).matches()) {
            return "邮箱格式不正确";
        }
        return null;
    }

    public static boolean isValidPhone(String phone) {
        return validatePhone(phone) == null;
    }

    public static boolean isValidEmail(String email) {
        return validateEmail(email) == null;
    }
}
