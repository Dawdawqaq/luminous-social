package com.luminous.api.notification;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.luminous.api.common.SnowflakeIdGenerator;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.luminous.api.auth.User;
import com.luminous.api.auth.UserMapper;
import com.luminous.api.common.BusinessException;
import com.luminous.api.common.PageResponse;
import com.luminous.api.security.CurrentUser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

    private final NotificationMapper notificationMapper;
    private final UserMapper userMapper;
    private final StringRedisTemplate stringRedisTemplate;

    private static final String LIKE_DEBOUNCE_PREFIX = "debouncing:like:";
    private static final Duration LIKE_DEBOUNCE_TTL = Duration.ofMinutes(5);

    public NotificationService(
            NotificationMapper notificationMapper,
            UserMapper userMapper,
            StringRedisTemplate stringRedisTemplate
    ) {
        this.notificationMapper = notificationMapper;
        this.userMapper = userMapper;
        this.stringRedisTemplate = stringRedisTemplate;
    }

    @Transactional
    public void handleEvent(NotificationEvent event) {
        if (event == null || event.receiverId() == null || event.actorId() == null) {
            return;
        }

        // 1. 防自发自收：用户自己的动作不产生通知
        if (event.receiverId().equals(event.actorId())) {
            log.debug("Skipping self-notification for user {}", event.actorId());
            return;
        }

        // 2. 点赞防抖与防刷限制：5 分钟内同一帖子重复点赞不重复通知
        if ("LIKE_POST".equals(event.type())) {
            String debounceKey = LIKE_DEBOUNCE_PREFIX + event.actorId() + ":" + event.targetId();
            try {
                Boolean isFirst = stringRedisTemplate.opsForValue().setIfAbsent(debounceKey, "1", LIKE_DEBOUNCE_TTL);
                if (Boolean.FALSE.equals(isFirst)) {
                    log.debug("Duplicate like notification debounced for key: {}", debounceKey);
                    return;
                }
            } catch (Exception e) {
                log.warn("Redis debounce check failed, proceeding with DB insert: {}", e.getMessage());
            }
        }

        // 3. 落库持久化
        Notification notification = new Notification();
        notification.setId(SnowflakeIdGenerator.getNextId());
        notification.setReceiverId(event.receiverId());
        notification.setActorId(event.actorId());
        notification.setType(event.type());
        notification.setTargetType(event.targetType());
        notification.setTargetId(event.targetId());
        notification.setContentPreview(event.contentPreview());
        notification.setIsRead(false);
        notification.setCreatedAt(LocalDateTime.now());

        notificationMapper.insert(notification);
        log.info("Created notification [id={}, type={}, receiverId={}]", notification.getId(), event.type(), event.receiverId());
    }

    public NotificationDtos.UnreadCountResponse getUnreadCount(CurrentUser currentUser) {
        if (currentUser == null) {
            return new NotificationDtos.UnreadCountResponse(0);
        }
        long count = notificationMapper.selectCount(new LambdaQueryWrapper<Notification>()
                .eq(Notification::getReceiverId, currentUser.id())
                .eq(Notification::getIsRead, false));
        return new NotificationDtos.UnreadCountResponse(count);
    }

    public PageResponse<NotificationDtos.NotificationDto> list(long page, long size, String type, CurrentUser currentUser) {
        if (currentUser == null) {
            throw new BusinessException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "请先登录后查看通知中心");
        }

        LambdaQueryWrapper<Notification> wrapper = new LambdaQueryWrapper<Notification>()
                .eq(Notification::getReceiverId, currentUser.id());

        // 分类过滤：ALL | LIKE | COMMENT | FOLLOW | SYSTEM
        if (type != null && !type.isBlank() && !type.equalsIgnoreCase("ALL")) {
            String upperType = type.trim().toUpperCase();
            switch (upperType) {
                case "LIKE" -> wrapper.eq(Notification::getType, "LIKE_POST");
                case "COMMENT" -> wrapper.in(Notification::getType, List.of("COMMENT_POST", "REPLY_COMMENT"));
                case "FOLLOW" -> wrapper.eq(Notification::getType, "FOLLOW_USER");
                case "SYSTEM" -> wrapper.eq(Notification::getType, "SYSTEM");
                default -> wrapper.eq(Notification::getType, upperType);
            }
        }

        wrapper.orderByDesc(Notification::getCreatedAt);

        IPage<Notification> notificationPage = notificationMapper.selectPage(new Page<>(page, size), wrapper);
        List<Notification> records = notificationPage.getRecords();
        if (records.isEmpty()) {
            return new PageResponse<>(List.of(), notificationPage.getCurrent(), notificationPage.getSize(), notificationPage.getTotal(), false);
        }

        // Batch-Loading: 批量拉取 Actor 用户信息，消除 N+1 查询
        Set<Long> actorIds = records.stream().map(Notification::getActorId).collect(Collectors.toSet());
        Map<Long, User> userMap = actorIds.isEmpty() ? Map.of() :
                userMapper.selectBatchIds(actorIds).stream()
                        .collect(Collectors.toMap(User::getId, Function.identity(), (a, b) -> a));

        List<NotificationDtos.NotificationDto> dtos = records.stream().map(n -> {
            User actor = userMap.get(n.getActorId());
            NotificationDtos.ActorDto actorDto = actor != null ?
                    new NotificationDtos.ActorDto(String.valueOf(actor.getId()), actor.getUsername(), actor.getDisplayName(), actor.getAvatarUrl()) :
                    new NotificationDtos.ActorDto(String.valueOf(n.getActorId()), "已注销用户", "已注销用户", null);

            // 根据主体类型精准生成目标标题与跳转路由
            String targetTitle;
            String targetUrl;
            if ("USER".equalsIgnoreCase(n.getTargetType())) {
                targetTitle = actor != null ? actor.getDisplayName() : null;
                targetUrl = actor != null ? "/user/" + actor.getUsername() : "/user/" + n.getTargetId();
            } else if ("COMMUNITY".equalsIgnoreCase(n.getTargetType())) {
                targetTitle = n.getContentPreview();
                targetUrl = "/community/" + n.getTargetId();
            } else {
                targetTitle = n.getContentPreview();
                targetUrl = "/post/" + n.getTargetId();
            }

            NotificationDtos.TargetDto targetDto = new NotificationDtos.TargetDto(
                    n.getTargetType(),
                    String.valueOf(n.getTargetId()),
                    targetTitle,
                    targetUrl
            );

            String createdAtStr = n.getCreatedAt() != null ?
                    n.getCreatedAt().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME) : "";

            return new NotificationDtos.NotificationDto(
                    String.valueOf(n.getId()),
                    n.getType(),
                    actorDto,
                    targetDto,
                    n.getContentPreview(),
                    Boolean.TRUE.equals(n.getIsRead()),
                    createdAtStr
            );
        }).toList();

        boolean hasNext = notificationPage.getCurrent() < notificationPage.getPages();
        return new PageResponse<>(dtos, notificationPage.getCurrent(), notificationPage.getSize(), notificationPage.getTotal(), hasNext);
    }

    @Transactional
    public void markAsRead(Long id, CurrentUser currentUser) {
        if (currentUser == null) {
            throw new BusinessException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "请先登录");
        }
        Notification notification = notificationMapper.selectById(id);
        if (notification == null) {
            throw new BusinessException(HttpStatus.NOT_FOUND, "NOT_FOUND", "通知不存在");
        }
        if (!notification.getReceiverId().equals(currentUser.id())) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "FORBIDDEN", "无权操作该通知");
        }
        notification.setIsRead(true);
        notificationMapper.updateById(notification);
    }

    @Transactional
    public NotificationDtos.MarkedCountResponse markAllAsRead(CurrentUser currentUser) {
        if (currentUser == null) {
            throw new BusinessException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "请先登录");
        }
        int markedCount = notificationMapper.markAllAsRead(currentUser.id());
        return new NotificationDtos.MarkedCountResponse(markedCount);
    }

    @Transactional
    public void deleteNotification(Long id, CurrentUser currentUser) {
        if (currentUser == null) {
            throw new BusinessException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "请先登录");
        }
        Notification notification = notificationMapper.selectById(id);
        if (notification == null) {
            throw new BusinessException(HttpStatus.NOT_FOUND, "NOT_FOUND", "通知不存在");
        }
        if (!notification.getReceiverId().equals(currentUser.id())) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "FORBIDDEN", "无权删除该通知");
        }
        notificationMapper.deleteById(id);
    }

    private String buildTargetUrl(String targetType, Long targetId) {
        if (targetType == null || targetId == null) return "/";
        return switch (targetType.toUpperCase()) {
            case "POST" -> "/post/" + targetId;
            case "COMMENT" -> "/post/" + targetId; // 评论通常挂载在帖子下
            case "USER" -> "/user/" + targetId;
            case "COMMUNITY" -> "/community/" + targetId;
            default -> "/";
        };
    }
}
