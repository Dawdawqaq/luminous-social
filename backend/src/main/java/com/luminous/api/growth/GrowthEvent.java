package com.luminous.api.growth;

import java.time.LocalDate;

/** 这些业务事件同步处理，异常回滚业务写入与成长写入，不使用提交后内存回调发奖励。 */
public final class GrowthEvent {
    private GrowthEvent() {}
    public record Registered(long userId) {}
    public record Joined(long userId, long communityId) {}
    public record CheckedIn(long userId, long communityId, LocalDate date) {}
    public record Created(String contentType, long contentId) {}
    public record Liked(String contentType, long contentId, long actorId) {}
    public record Removed(String contentType, long contentId, boolean moderated, String reason) {}
}
