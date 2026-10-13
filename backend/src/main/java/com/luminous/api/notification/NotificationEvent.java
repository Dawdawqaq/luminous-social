package com.luminous.api.notification;

import java.util.UUID;

/** 业务事件 ID 在首次创建时生成，所有发送、降级和重放都保留该身份。 */
public record NotificationEvent(Long actorId, Long receiverId, String type, String targetType, Long targetId,
                                String contentPreview, String eventId, Long postId, Long commentId,
                                java.time.LocalDateTime occurredAt) {
    public NotificationEvent(Long actorId, Long receiverId, String type, String targetType, Long targetId, String contentPreview, String eventId) {
        this(actorId,receiverId,type,targetType,targetId,contentPreview,eventId,null,null,null);
    }
    public NotificationEvent(Long actorId, Long receiverId, String type, String targetType, Long targetId, String contentPreview) {
        this(actorId, receiverId, type, targetType, targetId, contentPreview, UUID.randomUUID().toString(),
                "POST".equals(targetType) ? targetId : null, null, java.time.LocalDateTime.now());
    }

    public static NotificationEvent interaction(Long actorId, Long receiverId, String type, String targetType,
            Long targetId, String preview, Long postId, Long commentId) {
        return new NotificationEvent(actorId,receiverId,type,targetType,targetId,preview,UUID.randomUUID().toString(),
                postId,commentId,java.time.LocalDateTime.now());
    }
}
