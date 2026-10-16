package com.luminous.api.notification;

import com.luminous.api.common.ApiResponse;
import com.luminous.api.common.PageResponse;
import com.luminous.api.security.CurrentUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@Tag(name = "消息与通知中心", description = "用户互动通知、未读红点计数、已读标记与通知列表管理")
@RestController
@RequestMapping("/api/notifications")
public class NotificationController {

    private final NotificationService notificationService;

    public NotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @Operation(summary = "获取未读通知总数")
    @GetMapping("/unread-count")
    public ApiResponse<NotificationDtos.UnreadCountResponse> getUnreadCount(
            @AuthenticationPrincipal CurrentUser currentUser
    ) {
        return ApiResponse.ok(notificationService.getUnreadCount(currentUser));
    }

    @Operation(summary = "分页获取个人通知列表")
    @GetMapping
    public ApiResponse<PageResponse<NotificationDtos.NotificationDto>> list(
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "20") long size,
            @RequestParam(defaultValue = "ALL") String type,
            @AuthenticationPrincipal CurrentUser currentUser
    ) {
        return ApiResponse.ok(notificationService.list(page, size, type, currentUser));
    }

    @Operation(summary = "单条标记已读")
    @PutMapping("/{id}/read")
    public ApiResponse<Void> markAsRead(
            @PathVariable Long id,
            @RequestParam(required = false) Long version,
            @AuthenticationPrincipal CurrentUser currentUser
    ) {
        notificationService.markAsRead(id, version, currentUser);
        return ApiResponse.ok(null);
    }

    @Operation(summary = "按更新时间与编号游标获取收件箱，不统计全量历史")
    @GetMapping("/inbox")
    public ApiResponse<NotificationDtos.InboxSlice> inbox(@RequestParam(required=false) String cursor,
            @RequestParam(defaultValue="20") int size, @RequestParam(defaultValue="ALL") String type,
            @AuthenticationPrincipal CurrentUser user) {
        return ApiResponse.ok(notificationService.slice(cursor,size,type,user));
    }

    @Operation(summary = "获取各分类未读数量")
    @GetMapping("/summary")
    public ApiResponse<NotificationDtos.Summary> summary(@AuthenticationPrincipal CurrentUser user) {
        return ApiResponse.ok(notificationService.summary(user));
    }

    @Operation(summary = "一键全部标记已读")
    @PutMapping("/read-all")
    public ApiResponse<NotificationDtos.MarkedCountResponse> markAllAsRead(
            @AuthenticationPrincipal CurrentUser currentUser
    ) {
        return ApiResponse.ok(notificationService.markAllAsRead(currentUser));
    }

    @Operation(summary = "删除单条通知")
    @DeleteMapping("/{id}")
    public ApiResponse<Void> deleteNotification(
            @PathVariable Long id,
            @AuthenticationPrincipal CurrentUser currentUser
    ) {
        notificationService.deleteNotification(id, currentUser);
        return ApiResponse.ok(null);
    }
}
