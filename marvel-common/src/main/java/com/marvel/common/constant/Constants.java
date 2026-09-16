package com.marvel.common.constant;

public final class Constants {

    private Constants() {
    }

    /** 超级管理员角色标识 */
    public static final String SUPER_ADMIN_ROLE = "admin";
    /** 超级管理员用户 ID */
    public static final Long SUPER_ADMIN_USER_ID = 1L;

    /** 正常状态 */
    public static final String STATUS_NORMAL = "0";
    /** 停用状态 */
    public static final String STATUS_DISABLED = "1";

    /** 菜单类型：目录 */
    public static final String MENU_TYPE_DIR = "M";
    /** 菜单类型：菜单 */
    public static final String MENU_TYPE_MENU = "C";
    /** 菜单类型：按钮 */
    public static final String MENU_TYPE_BUTTON = "F";

    /** 是否：是 */
    public static final String YES = "0";
    /** 是否：否 */
    public static final String NO = "1";

    /** Redis key 前缀 */
    public static final String CAPTCHA_KEY_PREFIX = "marvel:captcha:";

    /** 系统参数：是否启用登录验证码 */
    public static final String CONFIG_CAPTCHA_ENABLED = "sys.captcha.enabled";
}
