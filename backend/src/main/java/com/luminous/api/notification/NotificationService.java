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
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

    private final NotificationMapper notificationMapper;
    private final UserMapper userMapper;
    private final org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;
    private final NotificationInboxRepository inbox;


    public NotificationService(
            NotificationMapper notificationMapper,
            UserMapper userMapper,
            org.springframework.jdbc.core.JdbcTemplate jdbcTemplate, NotificationInboxRepository inbox
    ) {
        this.notificationMapper = notificationMapper;
        this.userMapper = userMapper;
        this.jdbcTemplate = jdbcTemplate; this.inbox=inbox;
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

        inbox.insertBatch(List.of(event));
    }

    @Transactional
    public void handleEvents(List<NotificationEvent> events) { inbox.insertBatch(events); }

    public NotificationDtos.Summary summary(CurrentUser user) {
        requireUser(user);
        Map<String,Long> counts=inbox.unread(user.id());
        return new NotificationDtos.Summary(counts,counts.getOrDefault("ALL",0L),inbox.revision(user.id()));
    }

    public NotificationDtos.InboxSlice slice(String cursor, int size, String type, CurrentUser user) {
        requireUser(user);
        if(size<1 || size>50) throw new BusinessException(HttpStatus.BAD_REQUEST,"INVALID_PAGE_SIZE","每次加载数量应为 1 至 50 条");
        String category=type==null?"ALL":type.toUpperCase(Locale.ROOT);
        if(!Set.of("ALL","LIKE","COMMENT","FOLLOW","SYSTEM").contains(category))
            throw new BusinessException(HttpStatus.BAD_REQUEST,"INVALID_TYPE","通知分类无效");
        long before=Long.MAX_VALUE;
        LocalDateTime beforeTime=LocalDateTime.of(9999,12,31,23,59,59);
        if(cursor!=null && !cursor.isBlank()) {
            try {
                if(cursor.length()>180) throw new IllegalArgumentException();
                String[] fields=new String(Base64.getUrlDecoder().decode(cursor),java.nio.charset.StandardCharsets.UTF_8).split("\\|",-1);
                if(fields.length!=4 || !fields[0].equals(user.id().toString()) || !fields[1].equals(category)) throw new IllegalArgumentException();
                beforeTime=LocalDateTime.parse(fields[2]); before=Long.parseLong(fields[3]);
                if(before<=0 || beforeTime.getYear()<1970 || beforeTime.getYear()>9999) throw new IllegalArgumentException();
            } catch(RuntimeException e) { throw new BusinessException(HttpStatus.BAD_REQUEST,"INVALID_CURSOR","通知分页位置无效，请刷新列表"); }
        }
        List<Notification> fetched=inbox.slice(user.id(),category,beforeTime,before,size+1);
        List<Notification> rows=fetched.stream().limit(size).toList();
        boolean more=fetched.size()>size;
        String next=more?Base64.getUrlEncoder().withoutPadding().encodeToString((user.id()+"|"+category+"|"+rows.getLast().getUpdatedAt()+"|"+rows.getLast().getId()).getBytes(java.nio.charset.StandardCharsets.UTF_8)):null;
        return new NotificationDtos.InboxSlice(toDtos(rows),next,more);
    }

    private void requireUser(CurrentUser user) {
        if(user==null) throw new BusinessException(HttpStatus.UNAUTHORIZED,"UNAUTHORIZED","请先登录");
    }

    public NotificationDtos.UnreadCountResponse getUnreadCount(CurrentUser currentUser) {
        if (currentUser == null) {
            return new NotificationDtos.UnreadCountResponse(0);
        }
        long count = notificationMapper.selectCount(new LambdaQueryWrapper<Notification>()
                .eq(Notification::getReceiverId, currentUser.id())
                .eq(Notification::getIsRead, false).isNull(Notification::getDeletedAt));
        return new NotificationDtos.UnreadCountResponse(count);
    }

    public PageResponse<NotificationDtos.NotificationDto> list(long page, long size, String type, CurrentUser currentUser) {
        com.luminous.api.common.PaginationPolicy.validate(page, size);
        if (currentUser == null) {
            throw new BusinessException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "请先登录后查看通知中心");
        }

        LambdaQueryWrapper<Notification> wrapper = new LambdaQueryWrapper<Notification>()
                .eq(Notification::getReceiverId, currentUser.id()).isNull(Notification::getDeletedAt);

        // 分类过滤：ALL | LIKE | COMMENT | FOLLOW | SYSTEM
        if (type != null && !type.isBlank() && !type.equalsIgnoreCase("ALL")) {
            String upperType = type.trim().toUpperCase();
            switch (upperType) {
                case "LIKE" -> wrapper.in(Notification::getType, List.of("LIKE_POST","LIKE_COMMENT"));
                case "COMMENT" -> wrapper.in(Notification::getType, List.of("COMMENT_POST", "REPLY_COMMENT"));
                case "FOLLOW" -> wrapper.eq(Notification::getType, "FOLLOW_USER");
                case "SYSTEM" -> wrapper.eq(Notification::getType, "SYSTEM");
                default -> wrapper.eq(Notification::getType, upperType);
            }
        }

        wrapper.orderByDesc(Notification::getCreatedAt).orderByDesc(Notification::getId);

        IPage<Notification> notificationPage = notificationMapper.selectPage(new Page<>(page, size), wrapper);
        List<Notification> records = notificationPage.getRecords();
        if (records.isEmpty()) {
            return new PageResponse<>(List.of(), notificationPage.getCurrent(), notificationPage.getSize(), notificationPage.getTotal(), false);
        }

        List<NotificationDtos.NotificationDto> dtos=toDtos(records);

        boolean hasNext = notificationPage.getCurrent() < notificationPage.getPages();
        return new PageResponse<>(dtos, notificationPage.getCurrent(), notificationPage.getSize(), notificationPage.getTotal(), hasNext);
    }

    private List<NotificationDtos.NotificationDto> toDtos(List<Notification> records) {
        if(records.isEmpty()) return List.of();
        // 批量拉取发起者，消除逐条查询。
        Set<Long> actorIds = records.stream().map(Notification::getActorId).collect(Collectors.toSet());
        Map<Long, User> userMap = actorIds.isEmpty() ? Map.of() :
                userMapper.selectBatchIds(actorIds).stream()
                        .collect(Collectors.toMap(User::getId, Function.identity(), (a, b) -> a));

        return records.stream().map(n -> {
            User actor = userMap.get(n.getActorId());
            NotificationDtos.ActorDto actorDto = actor != null ?
                    new NotificationDtos.ActorDto(String.valueOf(actor.getId()), actor.getUsername(), actor.getDisplayName(), actor.getAvatarUrl()) :
                    new NotificationDtos.ActorDto(String.valueOf(n.getActorId()), "已注销用户", "已注销用户", null);

            // 根据主体类型精准生成目标标题与跳转路由
            String targetTitle;
            String targetUrl;
            if ("USER".equalsIgnoreCase(n.getTargetType())) {
                targetTitle = actor != null ? actor.getDisplayName() : null;
                targetUrl = actor != null ? "/u/" + actor.getUsername() : "/u/" + n.getTargetId();
            } else if ("COMMUNITY".equalsIgnoreCase(n.getTargetType())) {
                targetTitle = n.getContentPreview();
                targetUrl = "/community/" + n.getTargetId();
            } else {
                targetTitle = n.getContentPreview();
                Long postId=n.getPostId()==null?n.getTargetId():n.getPostId();
                targetUrl = "/post/" + postId + (n.getCommentId()==null?"":"?comment="+n.getCommentId());
            }

            NotificationDtos.TargetDto targetDto = new NotificationDtos.TargetDto(
                    n.getTargetType(),
                    String.valueOf(n.getTargetId()),
                    targetTitle,
                    targetUrl
            );

            String createdAtStr = n.getCreatedAt() != null ?
                    n.getCreatedAt().atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME) : "";
            String updatedAtStr=n.getUpdatedAt()==null?createdAtStr:n.getUpdatedAt().atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);

            return new NotificationDtos.NotificationDto(
                    String.valueOf(n.getId()),
                    n.getType(),
                    actorDto,
                    targetDto,
                    n.getContentPreview(),
                    Boolean.TRUE.equals(n.getIsRead()),
                    createdAtStr,
                    n.getInteractionCount()==null?1:n.getInteractionCount(),
                    updatedAtStr
            );
        }).toList();
    }

    @Transactional
    public void markAsRead(Long id, CurrentUser user) { markAsRead(id,null,user); }

    @Transactional
    public void markAsRead(Long id, Long version, CurrentUser user) {
        requireOwned(id,user);
        if(version!=null && version<1) throw new BusinessException(HttpStatus.BAD_REQUEST,"INVALID_VERSION","通知版本无效");
        inbox.read(id,user.id(),version);
    }
    @Transactional
    public NotificationDtos.MarkedCountResponse markAllAsRead(CurrentUser user) {
        requireUser(user); return new NotificationDtos.MarkedCountResponse(inbox.readAll(user.id()));
    }
    @Transactional
    public void deleteNotification(Long id, CurrentUser user) { requireOwned(id,user); inbox.delete(id,user.id()); }

    private void requireOwned(Long id,CurrentUser user) {
        requireUser(user);
        Notification n=notificationMapper.selectById(id);
        if(n==null || n.getDeletedAt()!=null) throw new BusinessException(HttpStatus.NOT_FOUND,"NOT_FOUND","通知不存在");
        if(!Objects.equals(n.getReceiverId(),user.id())) throw new BusinessException(HttpStatus.FORBIDDEN,"FORBIDDEN","无权操作该通知");
    }
}
