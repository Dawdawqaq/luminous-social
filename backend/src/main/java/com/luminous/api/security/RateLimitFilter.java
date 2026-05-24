package com.luminous.api.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 轻量级 IP 维度限流过滤器（无额外依赖）
 * 保护注册与登录接口，防止暴力破解与自动化刷号攻击。
 * 策略：固定窗口 60 秒内同一 IP 最多请求 10 次，超限返回 429。
 */
@Component
public class RateLimitFilter extends OncePerRequestFilter {

    /** 每个 IP 在当前窗口内的计数 */
    private final ConcurrentHashMap<String, AtomicInteger> counter = new ConcurrentHashMap<>();
    /** 每个 IP 的窗口起始时间（毫秒） */
    private final ConcurrentHashMap<String, Long> windowStart = new ConcurrentHashMap<>();

    private static final int WINDOW_SECONDS = 60;
    private static final int MAX_REQUESTS = 10;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getServletPath();
        String method = request.getMethod();
        // 仅拦截注册与登录接口
        return !("POST".equalsIgnoreCase(method) &&
                (path.equals("/api/auth/login") || path.equals("/api/auth/register")));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {

        String ip = resolveClientIp(request);
        long now = System.currentTimeMillis();

        // 窗口重置逻辑
        windowStart.compute(ip, (k, v) -> {
            if (v == null || (now - v) > WINDOW_SECONDS * 1000L) {
                counter.put(ip, new AtomicInteger(0));
                return now;
            }
            return v;
        });

        int count = counter.computeIfAbsent(ip, k -> new AtomicInteger(0)).incrementAndGet();

        if (count > MAX_REQUESTS) {
            response.setStatus(429);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setCharacterEncoding(StandardCharsets.UTF_8.name());
            response.getWriter().write(
                "{\"success\":false,\"error\":{\"code\":\"RATE_LIMIT_EXCEEDED\"," +
                "\"message\":\"请求过于频繁，请稍后再试\"}}"
            );
            return;
        }

        filterChain.doFilter(request, response);
    }

    private String resolveClientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
