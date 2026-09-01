package com.luminous.api.notification;

public record NotificationEvent(
        Long actorId,
        Long receiverId,
        String type,
        String targetType,
        Long targetId,
        String contentPreview
) {}
