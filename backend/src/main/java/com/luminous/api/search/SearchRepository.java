package com.luminous.api.search;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.*;
import org.springframework.stereotype.Repository;
import javax.sql.DataSource;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** 搜索只召回编号和轻量摘要；独立查询超时不修改其他业务的 JDBC 配置。 */
@Repository
public class SearchRepository {
    private final NamedParameterJdbcTemplate jdbc;
    public SearchRepository(DataSource source) {
        var template = new JdbcTemplate(source); template.setQueryTimeout(2);
        jdbc = new NamedParameterJdbcTemplate(template);
    }
    private record Source(String table, String visible, String columns, String titleColumns, List<String> names) {}
    private Source source(String kind) {
        return switch (kind) {
            case "posts" -> new Source("posts", "is_public=TRUE AND status IN ('NORMAL','PUBLISHED')", "title,content", "title", List.of("title"));
            case "communities" -> new Source("communities", "1=1", "slug,name,description", null, List.of("slug", "name"));
            case "users" -> new Source("users", "is_banned=FALSE", "username,display_name", null, List.of("username", "display_name"));
            default -> throw SearchTerms.invalid("请选择帖子、社区或用户");
        };
    }
    public List<Long> candidates(SearchTerms terms, String kind, int limit) {
        Source s = source(kind);
        var params = new MapSqlParameterSource().addValue("text", terms.text()).addValue("prefix", terms.prefix())
                .addValue("full", terms.booleanQuery()).addValue("take", limit);
        if (terms.userId() != null) return "users".equals(kind) ? jdbc.queryForList("SELECT id FROM users WHERE id=:id AND is_banned=FALSE", Map.of("id", terms.userId()), Long.class) : List.of();
        List<String> branches = new ArrayList<>();
        boolean full = !terms.booleanQuery().isBlank();
        String match = full ? "MATCH(" + s.columns() + ") AGAINST(:full IN BOOLEAN MODE)" : "0";
        String titleMatch = full && s.titleColumns() != null ? "MATCH(" + s.titleColumns() + ") AGAINST(:full IN BOOLEAN MODE)" : "0";
        // 多名称字段拆分支路，前缀按索引字段顺序取候选，避免按编号倒序遍历无关记录。
        for (String name : s.names()) {
            branches.add(branch(s, name + "=:text", "id DESC", "0", "0"));
            branches.add(branch(s, name + " LIKE :prefix ESCAPE '!'", name + " DESC,id DESC", "1", "0"));
        }
        String literalFilter = "";
        for (int i = 0; i < terms.literals().size(); i++) {
            params.addValue("literal" + i, terms.literals().get(i));
            literalFilter += " AND LOCATE(:literal" + i + ",CONCAT_WS(' '," + s.columns() + "))>0";
        }
        if (full) {
            branches.add(branch(s, match + ">0" + literalFilter, match + " DESC,id DESC", "3", match));
            if (s.titleColumns() != null) branches.add(branch(s, titleMatch + ">0" + literalFilter, titleMatch + " DESC,id DESC", "2", "(" + titleMatch + "*8+" + match + ")"));
        }
        // 各支路输出有界编号与分数，外层仅合并候选，避免为排序再次关联主表和重复全文匹配。
        String sql = "WITH candidate_scores AS (" + String.join(" UNION ALL ", branches)
                + ") SELECT id FROM candidate_scores GROUP BY id ORDER BY MIN(rank_order),MAX(relevance) DESC,id DESC LIMIT :take";
        return jdbc.queryForList(sql, params, Long.class);
    }
    private String branch(Source source, String condition, String order, String rank, String relevance) {
        return "SELECT id,rank_order,relevance FROM (SELECT id," + rank + " rank_order," + relevance + " relevance FROM " + source.table() + " WHERE " + source.visible() + " AND (" + condition + ") ORDER BY " + order + " LIMIT :take) candidate_branch";
    }
    public List<SearchDtos.Hit> hydrate(String kind, List<Long> ids) {
        if (ids.isEmpty()) return List.of();
        String sql = switch (kind) {
            case "posts" -> "SELECT p.id,p.title,LEFT(p.content,140) snippet,p.image_url image,u.username detail FROM posts p JOIN users u ON u.id=p.author_id WHERE p.id IN (:ids) AND p.is_public=TRUE AND p.status IN ('NORMAL','PUBLISHED')";
            case "communities" -> "SELECT id,name title,LEFT(description,140) snippet,icon_url image,slug detail FROM communities WHERE id IN (:ids)";
            case "users" -> "SELECT id,display_name title,LEFT(bio,140) snippet,avatar_url image,username detail FROM users WHERE id IN (:ids) AND is_banned=FALSE";
            default -> throw SearchTerms.invalid("搜索分类无效");
        };
        return jdbc.query(sql, Map.of("ids", ids), (row, index) -> {
            String id = row.getString("id"), detail = row.getString("detail");
            String path = switch (kind) { case "posts" -> "/post/" + id; case "communities" -> "/s/" + encode(detail); default -> "/u/" + encode(detail); };
            String subtitle = switch (kind) { case "posts" -> "帖子 · @" + detail; case "communities" -> "社区 · c/" + detail; default -> "用户 · @" + detail; };
            return new SearchDtos.Hit(id, kind, row.getString("title"), subtitle, row.getString("snippet"), row.getString("image"), path);
        });
    }
    private static String encode(String value) { return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20"); }
}
