package com.luminous.api.mq;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.luminous.api.common.BusinessException;
import com.luminous.api.notification.NotificationEvent;
import com.luminous.api.security.CurrentUser;
import com.luminous.api.vote.VoteTransactionService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;

/** 失败归档独立于失败业务事务；归档提交后才能向 Broker 确认终止重试。 */
@Service
public class FailedMessageService {
    private final FailedMessageRepository failures;
    private final ReliableEventRepository events;
    private final NotificationTransactionService notifications;
    private final VoteTransactionService votes;
    private final ObjectMapper mapper;
    public FailedMessageService(FailedMessageRepository failures, ReliableEventRepository events,
                                NotificationTransactionService notifications, VoteTransactionService votes, ObjectMapper mapper) {
        this.failures = failures; this.events = events; this.notifications = notifications; this.votes = votes; this.mapper = mapper;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void archive(String consumer, String eventId, String payload, int attempts, Throwable error) {
        ReliableEventRepository.Event event = events.find(eventId);
        if (event != null && "CONSUMED".equals(event.status())) { return; }
        failures.save(consumer, eventId, payload, attempts, error.toString());
        events.failed(eventId);
    }

    public List<FailedMessageDtos.FailureDto> list(int page, int size, CurrentUser user) {
        requireAdmin(user);
        int offset = (int) com.luminous.api.common.PaginationPolicy.validate(page, size);
        return failures.list(offset, size).stream()
                .map(failure -> new FailedMessageDtos.FailureDto(failure.id(), failure.consumer(), failure.eventId(),
                        failure.payload(), failure.attempts(), failure.lastError(), failure.status())).toList();
    }

    public void replay(long id, CurrentUser user) {
        requireAdmin(user);
        FailedMessageRepository.Failure failure = failures.find(id);
        if (failure == null) { throw new BusinessException(HttpStatus.NOT_FOUND, "FAILED_MESSAGE_NOT_FOUND", "失败消息不存在"); }
        try {
            if ("NOTIFICATION".equals(failure.consumer())) {
                notifications.process(failure.eventId(), mapper.readValue(failure.payload(), NotificationEvent.class));
            } else if ("VOTE".equals(failure.consumer())) {
                votes.process(mapper.readValue(failure.payload(), VoteMessage.class));
            } else { throw new IllegalArgumentException("未知消费类型"); }
        } catch (Exception ex) {
            // 未成功重放时保留失败记录，错误向管理员明确返回。
            throw new BusinessException(HttpStatus.CONFLICT, "REPLAY_FAILED", "重放失败，原失败记录已保留");
        }
    }

    private void requireAdmin(CurrentUser user) {
        if (user == null) { throw new BusinessException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "请先登录"); }
        if (!"ADMIN".equalsIgnoreCase(user.role())) { throw new BusinessException(HttpStatus.FORBIDDEN, "FORBIDDEN", "仅管理员可操作失败消息"); }
    }
}
