package com.luminous.api.post;

import com.luminous.api.common.BusinessException;
import com.luminous.api.security.CurrentUser;
import org.springframework.http.HttpStatus;

import java.util.Objects;

/** 帖子可见性规则：公开帖子允许读取，非公开帖子仅作者与管理员可访问。 */
public final class PostAccessPolicy {
    private PostAccessPolicy() {
    }

    public static boolean canRead(Post post, CurrentUser currentUser) {
        return post != null && (!"BANNED".equals(post.getStatus()) || currentUser != null && "ADMIN".equalsIgnoreCase(currentUser.role()))
                && (Boolean.TRUE.equals(post.getIsPublic())
                || currentUser != null && (Objects.equals(post.getAuthorId(), currentUser.id())
                || "ADMIN".equalsIgnoreCase(currentUser.role())));
    }

    public static void requireReadable(Post post, CurrentUser currentUser) {
        if (!canRead(post, currentUser)) {
            // 隐藏资源是否存在，避免通过错误响应枚举非公开帖子。
            throw new BusinessException(HttpStatus.NOT_FOUND, "POST_NOT_FOUND", "帖子不存在");
        }
    }
}
