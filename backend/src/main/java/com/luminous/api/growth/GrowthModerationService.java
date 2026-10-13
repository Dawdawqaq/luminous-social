package com.luminous.api.growth;

import com.luminous.api.cache.PostCacheService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class GrowthModerationService {
    private final GrowthService growth;
    private final PostCacheService cache;
    public GrowthModerationService(GrowthService growth, PostCacheService cache) { this.growth = growth; this.cache = cache; }
    @Transactional
    public void banPost(long postId, String reason) {
        growth.banPost(postId, reason);
        // 缓存服务自行安排提交后失效，不能在提交回调中再次注册回调。
        cache.invalidatePost(postId);
    }

    @Transactional
    public void revokeComment(long commentId, String reason) {
        growth.revokeComment(commentId, reason);
    }
}
