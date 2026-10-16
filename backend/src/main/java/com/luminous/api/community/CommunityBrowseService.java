package com.luminous.api.community;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.luminous.api.common.BusinessException;
import com.luminous.api.security.CurrentUser;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import java.util.List;
import java.util.Set;

/** 编号游标避免成员数变化造成跨页重复；筛选在数据库完成，单页最多三十条。 */
@Service
public class CommunityBrowseService {
    public static final Set<String> CATEGORIES = Set.of("Philosophy", "Craft", "Aesthetics", "Living", "Systems", "Literature");
    public record Slice(List<CommunityDtos.CommunityDto> items, String nextCursor, boolean hasNext) {}
    private final CommunityMapper mapper;
    private final CommunityService communities;
    public CommunityBrowseService(CommunityMapper mapper, CommunityService communities) { this.mapper = mapper; this.communities = communities; }

    public Slice list(String search, String category, String scope, String cursor, int size, CurrentUser viewer) {
        if (size < 1 || size > 30 || !Set.of("all", "joined").contains(scope)
                || (search != null && search.length() > 100) || (category != null && !category.isBlank() && !CATEGORIES.contains(category))) throw invalid();
        Long before = null;
        if (cursor != null && !cursor.isBlank()) {
            try { before = Long.valueOf(cursor); if (before <= 0) throw invalid(); }
            catch (NumberFormatException ex) { throw invalid(); }
        }
        if ("joined".equals(scope) && viewer == null) return new Slice(List.of(), null, false);
        var query = new LambdaQueryWrapper<Community>();
        if (before != null) query.lt(Community::getId, before);
        if (category != null && !category.isBlank()) query.eq(Community::getCategory, category);
        if (search != null && !search.isBlank()) {
            String text = search.trim().replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
            query.and(q -> q.like(Community::getSlug, text).or().like(Community::getName, text).or().like(Community::getDescription, text));
        }
        if ("joined".equals(scope)) query.apply("EXISTS (SELECT 1 FROM community_members cm WHERE cm.community_id=communities.id AND cm.user_id={0})", viewer.id());
        var rows = mapper.selectList(query.orderByDesc(Community::getId).last("LIMIT " + (size + 1)));
        boolean more = rows.size() > size;
        var page = rows.subList(0, Math.min(size, rows.size()));
        return new Slice(communities.assembleMany(page, viewer), more ? page.getLast().getId().toString() : null, more);
    }
    private BusinessException invalid() { return new BusinessException(HttpStatus.BAD_REQUEST, "INVALID_COMMUNITY_QUERY", "请检查社区搜索、分类和分页参数"); }
}
