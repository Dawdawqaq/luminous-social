package com.luminous.api.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.core.env.Environment;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

class RateLimitFilterTest {

    private final SlidingWindowRateLimiter rateLimiter = Mockito.mock(SlidingWindowRateLimiter.class);
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Environment environment = Mockito.mock(Environment.class);

    @Test
    @DisplayName("Dev 环境下携带 X-Bypass-Rate-Limit 头应放行")
    void testShouldNotFilterInDevWithBypassHeader() {
        when(environment.getActiveProfiles()).thenReturn(new String[]{"dev"});
        RateLimitFilter filter = new RateLimitFilter(rateLimiter, objectMapper, environment, new ClientIpResolver(""));

        HttpServletRequest request = Mockito.mock(HttpServletRequest.class);
        when(request.getHeader("X-Bypass-Rate-Limit")).thenReturn("true");
        when(request.getServletPath()).thenReturn("/api/auth/login");
        when(request.getMethod()).thenReturn("POST");

        assertTrue(filter.shouldNotFilter(request));
    }

    @Test
    @DisplayName("Prod 生产环境下携带 X-Bypass-Rate-Limit 头严禁放行")
    void testShouldFilterInProdEvenWithBypassHeader() {
        when(environment.getActiveProfiles()).thenReturn(new String[]{"prod"});
        RateLimitFilter filter = new RateLimitFilter(rateLimiter, objectMapper, environment, new ClientIpResolver(""));

        HttpServletRequest request = Mockito.mock(HttpServletRequest.class);
        when(request.getHeader("X-Bypass-Rate-Limit")).thenReturn("true");
        when(request.getServletPath()).thenReturn("/api/auth/login");
        when(request.getMethod()).thenReturn("POST");

        assertFalse(filter.shouldNotFilter(request));
    }

    @Test
    @DisplayName("非登录注册请求不进行限流过滤")
    void testShouldNotFilterNonAuthRequests() {
        when(environment.getActiveProfiles()).thenReturn(new String[]{"prod"});
        RateLimitFilter filter = new RateLimitFilter(rateLimiter, objectMapper, environment, new ClientIpResolver(""));

        HttpServletRequest request = Mockito.mock(HttpServletRequest.class);
        when(request.getHeader("X-Bypass-Rate-Limit")).thenReturn(null);
        when(request.getServletPath()).thenReturn("/api/posts");
        when(request.getMethod()).thenReturn("GET");

        assertTrue(filter.shouldNotFilter(request));
    }
}
