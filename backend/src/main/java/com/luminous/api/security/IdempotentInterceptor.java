package com.luminous.api.security;

import com.luminous.api.common.BusinessException;
import jakarta.servlet.http.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

/** MVC 层校验必填操作标识，事务与结果重放由安全过滤链中的 IdempotencyFilter 负责。 */
@Component
public class IdempotentInterceptor implements HandlerInterceptor {
    @Override public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (handler instanceof HandlerMethod method && method.hasMethodAnnotation(Idempotent.class)
                && (request.getHeader("X-Idempotency-Key") == null || request.getHeader("X-Idempotency-Key").isBlank())) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "MISSING_IDEMPOTENCY_KEY", "缺少操作标识 X-Idempotency-Key");
        }
        return true;
    }
}
