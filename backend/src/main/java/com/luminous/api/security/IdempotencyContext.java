package com.luminous.api.security;

/** 由服务端幂等过滤器创建，绑定身份、路径、操作标识和请求参数，供一次性凭证安全重试。 */
public record IdempotencyContext(String binding, long retentionSeconds) {
    public static final String REQUEST_ATTRIBUTE = IdempotencyContext.class.getName();
}
