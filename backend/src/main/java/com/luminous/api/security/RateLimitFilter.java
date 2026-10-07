package com.luminous.api.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.luminous.api.common.ApiResponse;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.env.Environment;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Arrays;
import java.util.Set;

/**
 * 分布式 IP 维度限流过滤器（基于 Redis）
 * 保护注册与登录接口，防止暴力破解与自动化刷号攻击。
 * 策略：60 秒内同一 IP 最多允许 60 次认证请求，超限返回 429。
 */
@Component
public class RateLimitFilter extends OncePerRequestFilter {
    private static final Logger log = LoggerFactory.getLogger(RateLimitFilter.class);

    private static final String RATE_LIMIT_PREFIX = "luminous:ratelimit:auth:";
    private static final Duration WINDOW_DURATION = Duration.ofSeconds(60);
    private static final int MAX_REQUESTS = 60;
    private static final Set<String> ALLOWED_BYPASS_PROFILES = Set.of("dev", "test", "local");

    private final SlidingWindowRateLimiter slidingWindowRateLimiter;
    private final ObjectMapper objectMapper;
    private final Environment environment;
    private final ClientIpResolver ipResolver;

    public RateLimitFilter(SlidingWindowRateLimiter slidingWindowRateLimiter, ObjectMapper objectMapper, Environment environment, ClientIpResolver ipResolver) {
        this.slidingWindowRateLimiter = slidingWindowRateLimiter;
        this.objectMapper = objectMapper;
        this.environment = environment;
        this.ipResolver = ipResolver;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        // 安全加固：X-Bypass-Rate-Limit 仅在 dev/test 环境放行，生产环境(prod)严格封堵任何客户端绕过尝试
        if ("true".equalsIgnoreCase(request.getHeader("X-Bypass-Rate-Limit")) && isBypassAllowedEnvironment()) {
            return true;
        }
        String path = request.getServletPath();
        String method = request.getMethod();
        // 仅拦截注册与登录接口
        return !(("POST".equalsIgnoreCase(method) || "GET".equalsIgnoreCase(method)) &&
                (path.equals("/api/auth/login") || path.equals("/api/auth/register") || path.startsWith("/api/auth/captcha")));
    }

    private boolean isBypassAllowedEnvironment() {
        String[] activeProfiles = environment.getActiveProfiles();
        if (activeProfiles == null || activeProfiles.length == 0) {
            return true; // 默认缺省 profile 视同开发环境
        }
        return Arrays.stream(activeProfiles).noneMatch("prod"::equals)
                && Arrays.stream(activeProfiles).anyMatch(ALLOWED_BYPASS_PROFILES::contains);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {

        String ip = ipResolver.resolve(request);
        String key = RATE_LIMIT_PREFIX + ip;

        boolean allowed;
        try { allowed = slidingWindowRateLimiter.isAllowed(key, MAX_REQUESTS, WINDOW_DURATION); }
        catch (com.luminous.api.common.BusinessException ex) {
            response.setStatus(ex.getStatus().value()); response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setCharacterEncoding(StandardCharsets.UTF_8.name());
            response.getWriter().write(objectMapper.writeValueAsString(ApiResponse.fail(ex.getCode(), ex.getMessage())));
            return;
        }
        if (!allowed) {
            log.warn("Sliding window rate limit exceeded for IP: {}", ip);
            response.setStatus(429);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setCharacterEncoding(StandardCharsets.UTF_8.name());
            response.getWriter().write(objectMapper.writeValueAsString(
                    ApiResponse.fail("RATE_LIMIT_EXCEEDED", "请求过于频繁，请 60 秒后再试")
            ));
            return;
        }

        filterChain.doFilter(request, response);
    }

}
