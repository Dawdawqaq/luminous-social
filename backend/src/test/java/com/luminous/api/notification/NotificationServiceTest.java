package com.luminous.api.notification;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.luminous.api.auth.User;
import com.luminous.api.auth.UserMapper;
import com.luminous.api.common.BusinessException;
import com.luminous.api.common.PageResponse;
import com.luminous.api.security.CurrentUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.http.HttpStatus;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    @Mock
    private NotificationMapper notificationMapper;

    @Mock
    private UserMapper userMapper;

    @Mock
    private StringRedisTemplate stringRedisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @InjectMocks
    private NotificationService notificationService;

    private CurrentUser currentUser;

    @BeforeEach
    void setUp() {
        currentUser = new CurrentUser(1001L, "receiver_user", "USER");
    }

    @Test
    void handleEvent_SelfNotification_Ignored() {
        NotificationEvent selfEvent = new NotificationEvent(
                1001L, 1001L, "LIKE_POST", "POST", 2001L, "My own post"
        );

        notificationService.handleEvent(selfEvent);

        verify(notificationMapper, never()).insert(any(Notification.class));
    }

    @Test
    void handleEvent_LikeDebounce_IgnoredWhenDuplicate() {
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.setIfAbsent(startsWith("debouncing:like:"), eq("1"), any(Duration.class)))
                .thenReturn(false);

        NotificationEvent duplicateLike = new NotificationEvent(
                1002L, 1001L, "LIKE_POST", "POST", 2001L, "Post Title"
        );

        notificationService.handleEvent(duplicateLike);

        verify(notificationMapper, never()).insert(any(Notification.class));
    }

    @Test
    void handleEvent_Success_WhenNewEvent() {
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.setIfAbsent(startsWith("debouncing:like:"), eq("1"), any(Duration.class)))
                .thenReturn(true);

        NotificationEvent newLike = new NotificationEvent(
                1002L, 1001L, "LIKE_POST", "POST", 2001L, "Post Title"
        );

        notificationService.handleEvent(newLike);

        verify(notificationMapper).insert(any(Notification.class));
    }

    @Test
    void getUnreadCount_Success() {
        when(notificationMapper.selectCount(any())).thenReturn(5L);

        NotificationDtos.UnreadCountResponse response = notificationService.getUnreadCount(currentUser);

        assertThat(response.unreadCount()).isEqualTo(5L);
    }

    @Test
    void list_Success_WithBatchLoading() {
        Notification n1 = new Notification();
        n1.setId(5001L);
        n1.setReceiverId(1001L);
        n1.setActorId(1002L);
        n1.setType("LIKE_POST");
        n1.setTargetType("POST");
        n1.setTargetId(2001L);
        n1.setContentPreview("赞了你的帖子");
        n1.setIsRead(false);
        n1.setCreatedAt(LocalDateTime.now());

        IPage<Notification> pageResult = new Page<>(1, 20);
        pageResult.setRecords(List.of(n1));
        pageResult.setTotal(1);
        pageResult.setPages(1);

        when(notificationMapper.selectPage(any(), any())).thenReturn(pageResult);

        User actor = new User();
        actor.setId(1002L);
        actor.setUsername("actor_user");
        actor.setDisplayName("Actor Display");
        actor.setAvatarUrl("https://example.com/avatar.jpg");

        when(userMapper.selectBatchIds(anyCollection())).thenReturn(List.of(actor));

        PageResponse<NotificationDtos.NotificationDto> result = notificationService.list(1, 20, "ALL", currentUser);

        assertThat(result.items()).hasSize(1);
        NotificationDtos.NotificationDto dto = result.items().get(0);
        assertThat(dto.id()).isEqualTo("5001");
        assertThat(dto.type()).isEqualTo("LIKE_POST");
        assertThat(dto.actor().username()).isEqualTo("actor_user");
        assertThat(dto.actor().displayName()).isEqualTo("Actor Display");
        assertThat(dto.target().targetUrl()).isEqualTo("/post/2001");
        assertThat(dto.isRead()).isFalse();
    }

    @Test
    void list_Success_FollowUserTargetUrl() {
        Notification n1 = new Notification();
        n1.setId(5002L);
        n1.setReceiverId(1001L);
        n1.setActorId(1002L);
        n1.setType("FOLLOW_USER");
        n1.setTargetType("USER");
        n1.setTargetId(1002L);
        n1.setContentPreview("actor_user 关注了你");
        n1.setIsRead(false);
        n1.setCreatedAt(LocalDateTime.now());

        IPage<Notification> pageResult = new Page<>(1, 20);
        pageResult.setRecords(List.of(n1));
        pageResult.setTotal(1);
        pageResult.setPages(1);

        when(notificationMapper.selectPage(any(), any())).thenReturn(pageResult);

        User actor = new User();
        actor.setId(1002L);
        actor.setUsername("actor_user");
        actor.setDisplayName("Actor Display");

        when(userMapper.selectBatchIds(anyCollection())).thenReturn(List.of(actor));

        PageResponse<NotificationDtos.NotificationDto> result = notificationService.list(1, 20, "ALL", currentUser);

        assertThat(result.items()).hasSize(1);
        NotificationDtos.NotificationDto dto = result.items().get(0);
        assertThat(dto.target().targetUrl()).isEqualTo("/user/actor_user");
        assertThat(dto.target().title()).isEqualTo("Actor Display");
    }

    @Test
    void markAsRead_Success() {
        Notification n = new Notification();
        n.setId(5001L);
        n.setReceiverId(1001L);
        n.setIsRead(false);

        when(notificationMapper.selectById(5001L)).thenReturn(n);

        notificationService.markAsRead(5001L, currentUser);

        assertThat(n.getIsRead()).isTrue();
        verify(notificationMapper).updateById(n);
    }

    @Test
    void markAsRead_Forbidden_WhenNotOwner() {
        Notification n = new Notification();
        n.setId(5001L);
        n.setReceiverId(9999L); // different receiver

        when(notificationMapper.selectById(5001L)).thenReturn(n);

        assertThatThrownBy(() -> notificationService.markAsRead(5001L, currentUser))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> {
                    BusinessException be = (BusinessException) ex;
                    assertThat(be.getStatus()).isEqualTo(HttpStatus.FORBIDDEN);
                });
    }

    @Test
    void markAllAsRead_Success() {
        when(notificationMapper.markAllAsRead(1001L)).thenReturn(3);

        NotificationDtos.MarkedCountResponse response = notificationService.markAllAsRead(currentUser);

        assertThat(response.markedCount()).isEqualTo(3);
    }

    @Test
    void deleteNotification_Success() {
        Notification n = new Notification();
        n.setId(5001L);
        n.setReceiverId(1001L);

        when(notificationMapper.selectById(5001L)).thenReturn(n);

        notificationService.deleteNotification(5001L, currentUser);

        verify(notificationMapper).deleteById(5001L);
    }
}
