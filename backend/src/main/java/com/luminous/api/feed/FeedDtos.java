package com.luminous.api.feed;

public final class FeedDtos {
    private FeedDtos() {}
    
    // ScrollResult for cursor-based pagination using SortedSet scores
    public record ScrollResult<T>(java.util.List<T> items, Long minScore, int offset, boolean hasMore) {}
}
