package com.marvel.common.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 标记可被「定时任务」调度器反射调用的方法。
 *
 * <p>安全约束：调度器的 invokeTarget 来自数据库 / 管理接口，属用户可控输入。
 * 只有显式标注本注解的公开无参方法才允许被调用，避免攻击者通过
 * {@code beanName.method} 调用容器内任意 Bean 的任意方法（CWE-470 不安全反射，潜在 RCE）。
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface JobTarget {
}
