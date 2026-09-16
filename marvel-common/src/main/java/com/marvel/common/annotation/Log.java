package com.marvel.common.annotation;

import java.lang.annotation.*;

/**
 * 操作日志注解：标注在 Controller 写方法上，由 framework 的切面拦截，
 * 发布 {@code OperLogEvent}（由 system 模块监听落库 sys_oper_log）。
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface Log {

    /** 模块标题，如「用户管理」 */
    String title();

    /** 业务类型：INSERT/UPDATE/DELETE/EXPORT 等，写入 method 列前缀便于分类 */
    BusinessType businessType() default BusinessType.OTHER;

    enum BusinessType {
        OTHER, INSERT, UPDATE, DELETE, IMPORT, EXPORT, GRANT, FORCE, CLEAN
    }
}
