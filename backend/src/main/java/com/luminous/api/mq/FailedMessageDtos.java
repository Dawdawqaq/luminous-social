package com.luminous.api.mq;

/** 管理员失败消息接口的数据结构，与数据库映射解耦。 */
public final class FailedMessageDtos {
    private FailedMessageDtos() {}
    public record FailureDto(String id, String consumer, String eventId, String payload,
                             int attempts, String lastError, String status) {}
}
