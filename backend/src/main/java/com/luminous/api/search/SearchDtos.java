package com.luminous.api.search;

import java.util.List;

public final class SearchDtos {
    private SearchDtos() {}
    public record Hit(String id, String kind, String title, String subtitle, String snippet, String image, String url) {}
    public record Group(String kind, List<Hit> items, boolean hasMore, boolean unavailable) {}
    public record Suggestions(List<Group> groups, String hint) {}
    public record Slice(String kind, List<Hit> items, String nextCursor, boolean hasMore, boolean windowLimited, String hint) {}
}
