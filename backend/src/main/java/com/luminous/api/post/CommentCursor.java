package com.luminous.api.post;

import com.luminous.api.common.BusinessException;
import org.springframework.http.HttpStatus;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Base64;

/** 游标绑定帖子和查询范围；主楼使用票数、时间、编号，楼内只使用时间、编号。 */
public record CommentCursor(int votes, LocalDateTime createdAt, long id) {
    public static String replyScope(long rootId) {
        // 时间排序采用独立范围标记，不沿用旧票数排序的分页位置。
        return "replies-time:" + rootId;
    }

    public String encode(long postId, String scope) {
        String text = postId + "|" + scope + "|" + votes + "|" + createdAt + "|" + id;
        return Base64.getUrlEncoder().withoutPadding().encodeToString(text.getBytes(StandardCharsets.UTF_8));
    }

    public static CommentCursor decode(String value, long postId, String scope) {
        if (value == null || value.isBlank()) return null;
        try {
            if (value.length() > 180) throw new IllegalArgumentException();
            String[] parts = new String(Base64.getUrlDecoder().decode(value), StandardCharsets.UTF_8).split("\\|", -1);
            if (parts.length != 5 || Long.parseLong(parts[0]) != postId || !parts[1].equals(scope)) throw new IllegalArgumentException();
            long id = Long.parseLong(parts[4]);
            if (id <= 0) throw new IllegalArgumentException();
            return new CommentCursor(Integer.parseInt(parts[2]), LocalDateTime.parse(parts[3]), id);
        } catch (RuntimeException exception) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "INVALID_COMMENT_CURSOR", "评论加载位置无效，请刷新后重试");
        }
    }

    public static void validateSize(int size) {
        if (size < 1 || size > 30) throw new BusinessException(HttpStatus.BAD_REQUEST, "INVALID_COMMENT_SIZE", "每批评论数量应在 1 至 30 之间");
    }
}
