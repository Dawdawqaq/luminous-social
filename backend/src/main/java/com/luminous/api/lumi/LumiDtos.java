package com.luminous.api.lumi;

/** 任务状态只返回调用者自己的记录，外部响应、密钥和内部提示词不进入 DTO。 */
public final class LumiDtos {
    private LumiDtos() {}
    public record Task(String id, String commentId, String status, String message, String replyId, boolean retryable) {}
    public record Status(boolean enabled, String name, boolean webSearchAvailable, java.util.List<Task> tasks) {}
    public record Requested(long commentId) {}
}
