package com.luminous.api.search;

import com.luminous.api.common.BusinessException;
import com.luminous.api.security.*;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.HexFormat;

/** 搜索读流量单独限速；仅信任配置代理提供的来源，不提供绕过请求头。 */
@Component
public class SearchRequestGuard {
    private final SlidingWindowRateLimiter limiter;
    private final ClientIpResolver ips;
    public SearchRequestGuard(SlidingWindowRateLimiter limiter, ClientIpResolver ips) { this.limiter = limiter; this.ips = ips; }
    public void check(CurrentUser user, HttpServletRequest request) {
        String ip;
        try { ip = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(ips.resolve(request).getBytes(StandardCharsets.UTF_8))); }
        catch (Exception ex) { throw new IllegalStateException("搜索来源摘要计算失败", ex); }
        String actor = user == null ? "anonymous:" + ip : "user:" + user.id();
        if (!limiter.isAllowed("luminous:search:limit:ip:" + ip, 300, Duration.ofMinutes(1))
                || !limiter.isAllowed("luminous:search:limit:" + actor, user == null ? 60 : 120, Duration.ofMinutes(1))) {
            throw new BusinessException(HttpStatus.TOO_MANY_REQUESTS, "SEARCH_RATE_LIMITED", "搜索有点频繁，请稍后再试");
        }
    }
}
