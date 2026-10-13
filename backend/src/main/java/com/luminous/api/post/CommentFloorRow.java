package com.luminous.api.post;

import java.time.LocalDateTime;

/** 查询楼层排序字段，不读取未展开的楼内正文。 */
public record CommentFloorRow(Long id, int votes, LocalDateTime createdAt, boolean rootPresent) {}
