package com.luminous.api.feed;

public final class FeedDtos {
    private FeedDtos() {}
    
    // 按创建时间分页，offset 表示已读取的同毫秒帖子数量。
    public record ScrollResult<T>(java.util.List<T> items, Long minScore, int offset, boolean hasMore) {}
}
