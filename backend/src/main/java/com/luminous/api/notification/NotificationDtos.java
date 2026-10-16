package com.luminous.api.notification;

public class NotificationDtos {

    public record NotificationDto(
            String id,
            String type,
            ActorDto actor,
            TargetDto target,
            String contentPreview,
            boolean isRead,
            String createdAt,
            long interactionCount,
            String updatedAt
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

    public record InboxSlice(java.util.List<NotificationDto> items, String nextCursor, boolean hasNext) {}
    public record Summary(java.util.Map<String,Long> unread, long unreadCount, String revision) {}
}
