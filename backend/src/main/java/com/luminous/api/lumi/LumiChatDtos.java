package com.luminous.api.lumi;

import jakarta.validation.constraints.*;
import java.util.List;

/** 浏览器只提交自己的问题、当前对话编号和操作标识，不能提交作者或伪造历史。 */
public final class LumiChatDtos {
    private LumiChatDtos(){}
    public record Send(@NotBlank @Pattern(regexp="[0-9]{1,19}") String sessionId,
                       @NotBlank @Pattern(regexp="[A-Za-z0-9-]{16,64}") String requestKey,
                       @NotBlank @Size(max=1000) String content){}
    public record Reset(@NotBlank @Pattern(regexp="[0-9]{1,19}") String sessionId){}
    public record Create(@NotBlank @Pattern(regexp="[A-Za-z0-9-]{16,64}") String requestKey){}
    public record Rename(@NotBlank @Size(max=40) String title){}
    public record Turn(String id,String question,String answer,String status,String message,boolean retryable,String createdAt,String revision,String sessionId){}
    public record Page(String sessionId,boolean enabled,int maxLength,int cooldownRemaining,List<Turn> turns,boolean hasMore,String nextCursor,String title,boolean busy,String activeSessionId,String activeTurnId){}
    public record Conversation(String id,String title,String activityAt,boolean active){}
    public record Conversations(List<Conversation> items,boolean hasMore,String nextCursor,int maxConversations,boolean busy,String activeSessionId){}
}
