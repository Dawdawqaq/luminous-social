package com.luminous.api.security;

import java.lang.annotation.*;

/** 标记必须携带操作标识的写接口；保留期由 app.idempotency.retention-seconds 统一控制。 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Idempotent {}
