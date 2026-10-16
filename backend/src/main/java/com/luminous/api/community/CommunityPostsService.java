package com.luminous.api.community;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.luminous.api.common.BusinessException;
import com.luminous.api.post.*;
import com.luminous.api.security.CurrentUser;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import java.util.List;

/** 社区最新流使用不可变编号续页，不受新增帖子或投票数变化影响。 */
@Service
public class CommunityPostsService {
    public record Slice(List<PostDtos.PostDto> items, String nextCursor, boolean hasMore) {}
    private final CommunityService communities;
    private final PostMapper mapper;
    private final PostService posts;
    public CommunityPostsService(CommunityService communities, PostMapper mapper, PostService posts) { this.communities = communities; this.mapper = mapper; this.posts = posts; }
    public Slice list(String slug, String cursor, int size, CurrentUser viewer) {
        if (size < 1 || size > 30) throw invalid();
        Long before = null;
        try { if (cursor != null && !cursor.isBlank()) { before = Long.valueOf(cursor); if (before <= 0) throw invalid(); } }
        catch (NumberFormatException ex) { throw invalid(); }
        var community = communities.requireBySlug(slug);
        var query = new LambdaQueryWrapper<Post>().eq(Post::getCommunityId, community.getId())
                .eq(Post::getIsPublic, true).in(Post::getStatus, "NORMAL", "PUBLISHED");
        if (before != null) query.lt(Post::getId, before);
        var rows = mapper.selectList(query.orderByDesc(Post::getId).last("LIMIT " + (size + 1)));
        boolean more = rows.size() > size;
        var page = rows.subList(0, Math.min(size, rows.size()));
        return new Slice(posts.assemblePostDtos(page, viewer), more ? page.getLast().getId().toString() : null, more);
    }
    private BusinessException invalid() { return new BusinessException(HttpStatus.BAD_REQUEST, "INVALID_COMMUNITY_CURSOR", "社区帖子分页参数无效"); }
}
