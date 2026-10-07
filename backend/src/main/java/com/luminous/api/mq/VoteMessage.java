package com.luminous.api.mq;

import java.io.Serializable;

/** 业务版本由数据库在同一帖子行锁下递增，不使用本机时间判断操作顺序。 */
public record VoteMessage(Long postId, Long userId, int value, int delta, long timestamp, String msgId, long version) implements Serializable {
    /** 兼容旧调用的构造形式；无版本消息会被消费入口拒绝，不能覆盖新投票。 */
    public VoteMessage(Long postId, Long userId, int value, int delta, long timestamp, String msgId) {
        this(postId, userId, value, delta, timestamp, msgId, 0);
    }
}
