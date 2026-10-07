package com.luminous.api.cache;

/**
 * Redis 键前缀与缓存有效期常量。
 */
public final class CacheConstants {

    private CacheConstants() {
        // 工具类不允许实例化。
    }

    public static final String POST_DETAIL_KEY = "luminous:cache:post:detail:v2:";
    // 共享缓存最多保留五秒，新前缀隔离旧版本的长有效期数据。
    public static final long POST_DETAIL_TTL_SECONDS = 5;
    
    public static final String USER_STATS_KEY = "luminous:cache:user:stats:";
    public static final String POST_UV_KEY = "luminous:stat:post:uv:";
    public static final String FEED_INBOX_KEY = "luminous:feed:inbox:";
    public static final String CHECKIN_KEY = "luminous:checkin:community:";
    public static final String LOCK_CACHE_REBUILD = "luminous:lock:cache:rebuild:";
    public static final String LOCK_JOIN_COMMUNITY = "luminous:lock:community:join:";
    public static final String IDEMPOTENT_KEY = "luminous:idempotent:";
}
