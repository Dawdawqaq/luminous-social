package com.luminous.api.integration;

import org.springframework.test.context.ActiveProfilesResolver;

/** 允许在明确指定时用内存数据库和独立 Redis 验证投票链路。 */
public class VoteTestProfileResolver implements ActiveProfilesResolver {
    @Override
    public String[] resolve(Class<?> testClass) {
        return new String[]{Boolean.getBoolean("luminous.vote-test.isolated")
                ? "vote-validation" : "integration"};
    }
}
