package com.luminous.api.notification;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

@TableName("notifications")
public class Notification {
    @TableId
    private Long id;
    private Long receiverId;
    private Long actorId;
    private String type;
    private String targetType;
    private Long targetId;
    private String contentPreview;
    private Boolean isRead;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime readAt;
    private LocalDateTime deletedAt;
    private Long interactionCount;
    private Long readVersion;
    private String aggregateKey;
    private Long postId;
    private Long commentId;

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime value) { updatedAt=value; }
    public LocalDateTime getReadAt() { return readAt; }
    public void setReadAt(LocalDateTime value) { readAt=value; }
    public LocalDateTime getDeletedAt() { return deletedAt; }
    public void setDeletedAt(LocalDateTime value) { deletedAt=value; }
    public Long getInteractionCount() { return interactionCount; }
    public void setInteractionCount(Long value) { interactionCount=value; }
    public Long getReadVersion() { return readVersion; }
    public void setReadVersion(Long value) { readVersion=value; }
    public String getAggregateKey() { return aggregateKey; }
    public void setAggregateKey(String value) { aggregateKey=value; }
    public Long getPostId() { return postId; }
    public void setPostId(Long value) { postId=value; }
    public Long getCommentId() { return commentId; }
    public void setCommentId(Long value) { commentId=value; }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getReceiverId() {
        return receiverId;
    }

    public void setReceiverId(Long receiverId) {
        this.receiverId = receiverId;
    }

    public Long getActorId() {
        return actorId;
    }

    public void setActorId(Long actorId) {
        this.actorId = actorId;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getTargetType() {
        return targetType;
    }

    public void setTargetType(String targetType) {
        this.targetType = targetType;
    }

    public Long getTargetId() {
        return targetId;
    }

    public void setTargetId(Long targetId) {
        this.targetId = targetId;
    }

    public String getContentPreview() {
        return contentPreview;
    }

    public void setContentPreview(String contentPreview) {
        this.contentPreview = contentPreview;
    }

    public Boolean getIsRead() {
        return isRead;
    }

    public void setIsRead(Boolean isRead) {
        this.isRead = isRead;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
