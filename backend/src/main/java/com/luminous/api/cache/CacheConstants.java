package com.luminous.api.cache;

/**
 * Cache constants containing Redis key prefixes and TTL values.
 */
public final class CacheConstants {

    private CacheConstants() {
        // Prevent instantiation
    }

    public static final String POST_DETAIL_KEY = "cache:post:detail:";
    // TTL in minutes
    public static final long POST_DETAIL_TTL_MINUTES = 30;
    public static final long POST_DETAIL_NULL_TTL_MINUTES = 2;
    
    public static final String USER_STATS_KEY = "cache:user:stats:";
    public static final String POST_UV_KEY = "stat:post:uv:";
    public static final String FEED_INBOX_KEY = "feed:inbox:";
    public static final String CHECKIN_KEY = "checkin:community:";
    public static final String LOCK_CACHE_REBUILD = "lock:cache:rebuild:";
    public static final String LOCK_JOIN_COMMUNITY = "lock:community:join:";
    public static final String IDEMPOTENT_KEY = "idempotent:";
}
