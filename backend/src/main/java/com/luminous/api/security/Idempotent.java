package com.luminous.api.security;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 接口幂等性校验注解
 * 配合 IdempotentInterceptor 拦截带有该注解的 Controller 方法或请求头 X-Idempotency-Key
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Idempotent {

    /**
     * 幂等 Key 前缀
     */
    String prefix() default "idempotent:";

    /**
     * 幂等 Token 有效期（秒），默认 120 秒 (2分钟)
     */
    int expireSeconds() default 120;

    /**
     * 请求头名称，用于提取幂等 Token
     */
    String headerName() default "X-Idempotency-Key";
}
