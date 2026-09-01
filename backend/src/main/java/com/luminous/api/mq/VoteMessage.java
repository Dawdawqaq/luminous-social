package com.luminous.api.mq;

import java.io.Serializable;

public record VoteMessage(
        Long postId,
        Long userId,
        int value,
        int delta,
        long timestamp,
        String msgId
) implements Serializable {
}
