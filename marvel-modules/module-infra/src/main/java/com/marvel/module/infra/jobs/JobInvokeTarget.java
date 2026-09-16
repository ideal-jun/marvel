package com.marvel.module.infra.jobs;

import java.util.regex.Pattern;

/**
 * 定时任务调用目标（invokeTarget）格式工具：{@code beanName.method}。
 *
 * <p>invokeTarget 来自数据库/管理接口，属用户可控输入。这里集中做格式白名单校验，
 * 拒绝括号、类名、静态调用等危险写法；执行侧再叠加 {@link com.marvel.common.annotation.JobTarget}
 * 方法级白名单，双重限制不安全反射（CWE-470）。
 */
public final class JobInvokeTarget {

    /** 仅允许：字母/数字/下划线组成的 beanName + '.' + 方法名 */
    private static final Pattern PATTERN =
            Pattern.compile("^[A-Za-z][A-Za-z0-9_]*\\.[A-Za-z][A-Za-z0-9_]*$");

    private JobInvokeTarget() {
    }

    /** 格式是否合法（会先 trim） */
    public static boolean isValidFormat(String invokeTarget) {
        return invokeTarget != null && PATTERN.matcher(invokeTarget.trim()).matches();
    }

    /** 取 beanName，调用前需先通过 {@link #isValidFormat(String)} */
    public static String beanName(String invokeTarget) {
        String target = invokeTarget.trim();
        return target.substring(0, target.lastIndexOf('.'));
    }

    /** 取方法名，调用前需先通过 {@link #isValidFormat(String)} */
    public static String methodName(String invokeTarget) {
        String target = invokeTarget.trim();
        return target.substring(target.lastIndexOf('.') + 1);
    }
}
