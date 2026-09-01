package com.luminous.api.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.luminous.api.common.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import java.nio.charset.StandardCharsets;
import java.time.Duration;

/**
 * 分布式接口幂等性拦截器
 * 核心逻辑：
 * 1. 检查方法上是否标注 @Idempotent 注解或请求头包含 X-Idempotency-Key
 * 2. 基于 Redis SETNX 原子指令锁定幂等键
 * 3. 若并发或重复提交相同 Token，立即返回 409 CONFLICT 错误
 */
@Component
public class IdempotentInterceptor implements HandlerInterceptor {

    private static final Logger log = LoggerFactory.getLogger(IdempotentInterceptor.class);

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    public IdempotentInterceptor(StringRedisTemplate redisTemplate, ObjectMapper objectMapper) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        if (!(handler instanceof HandlerMethod handlerMethod)) {
            return true;
        }

        Idempotent idempotent = handlerMethod.getMethodAnnotation(Idempotent.class);
        String headerName = idempotent != null ? idempotent.headerName() : "X-Idempotency-Key";
        String token = request.getHeader(headerName);

        // 如果既无注解且请求头中也无幂等 Key，则放行
        if (idempotent == null && (token == null || token.isBlank())) {
            return true;
        }

        if (token == null || token.isBlank()) {
            // 声明了 @Idempotent 必须传递 Token
            response.setStatus(HttpStatus.BAD_REQUEST.value());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setCharacterEncoding(StandardCharsets.UTF_8.name());
            response.getWriter().write(objectMapper.writeValueAsString(
                    ApiResponse.fail("MISSING_IDEMPOTENCY_KEY", "缺少幂等性请求头: " + headerName)
            ));
            return false;
        }

        String prefix = idempotent != null ? idempotent.prefix() : "idempotent:";
        int expireSeconds = idempotent != null ? idempotent.expireSeconds() : 120;
        String redisKey = prefix + token;

        try {
            Boolean isSuccess = redisTemplate.opsForValue().setIfAbsent(redisKey, "LOCKED", expireSeconds, java.util.concurrent.TimeUnit.SECONDS);
            if (Boolean.FALSE.equals(isSuccess)) {
                log.warn("Duplicate request rejected by idempotency key: {}", token);
                response.setStatus(HttpStatus.CONFLICT.value());
                response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                response.setCharacterEncoding(StandardCharsets.UTF_8.name());
                response.getWriter().write(objectMapper.writeValueAsString(
                        ApiResponse.fail("DUPLICATE_REQUEST", "请勿重复提交相同请求")
                ));
                return false;
            }
            // 记录到请求上下文中，以便在业务失败时做清理
            request.setAttribute("IDEMPOTENT_REDIS_KEY", redisKey);
        } catch (Exception e) {
            log.warn("Idempotency check failed in Redis, allowing gracefully: {}", e.getMessage());
        }

        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        String redisKey = (String) request.getAttribute("IDEMPOTENT_REDIS_KEY");
        if (redisKey != null) {
            // 若请求发生 4xx 业务校验错误或 5xx 异常，则立即释放幂等锁允许用户修改后重试
            if (ex != null || (response.getStatus() >= 400 && response.getStatus() != HttpStatus.CONFLICT.value())) {
                try {
                    redisTemplate.delete(redisKey);
                    log.debug("Released idempotency key [{}] due to request failure (status: {})", redisKey, response.getStatus());
                } catch (Exception e) {
                    log.warn("Failed to delete idempotency key on error: {}", redisKey, e);
                }
            }
        }
    }
}
