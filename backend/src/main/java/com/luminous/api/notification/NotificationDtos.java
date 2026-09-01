package com.luminous.api.notification;

public class NotificationDtos {

    public record NotificationDto(
            String id,
            String type,
            ActorDto actor,
            TargetDto target,
            String contentPreview,
            boolean isRead,
            String createdAt
    ) {}

    public record ActorDto(
            String id,
            String username,
            String displayName,
            String avatar
    ) {}

    public record TargetDto(
            String type,
            String id,
            String title,
            String targetUrl
    ) {}

    public record UnreadCountResponse(
            long unreadCount
    ) {}

    public record MarkedCountResponse(
            int markedCount
    ) {}
}
