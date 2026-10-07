package com.luminous.api.notification;

import java.util.UUID;

/** 业务事件 ID 在首次创建时生成，所有发送、降级和重放都保留该身份。 */
public record NotificationEvent(Long actorId, Long receiverId, String type, String targetType, Long targetId, String contentPreview, String eventId) {
    public NotificationEvent(Long actorId, Long receiverId, String type, String targetType, Long targetId, String contentPreview) {
        this(actorId, receiverId, type, targetType, targetId, contentPreview, UUID.randomUUID().toString());
    }
}
