package com.luminous.api.common;

import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {
    @ExceptionHandler(com.luminous.api.post.PostRateLimitException.class)
    public ResponseEntity<ApiResponse<Void>> handlePostRateLimit(com.luminous.api.post.PostRateLimitException ex) {
        return ResponseEntity.status(ex.getStatus()).header("Retry-After", String.valueOf(ex.getRetryAfterSeconds()))
                .body(ApiResponse.fail(ex.getCode(), ex.getMessage()));
    }
    @ExceptionHandler(com.luminous.api.auth.FollowRateLimitException.class)
    public ResponseEntity<ApiResponse<Void>> handleFollowRateLimit(com.luminous.api.auth.FollowRateLimitException ex) {
        return ResponseEntity.status(ex.getStatus()).header("Retry-After",String.valueOf(ex.getRetryAfterSeconds()))
                .body(ApiResponse.fail(ex.getCode(),ex.getMessage()));
    }
    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /** 浏览器主动关闭流连接后响应已不可写，不再回写错误 JSON 或误报服务器故障。 */
    @ExceptionHandler(org.springframework.web.context.request.async.AsyncRequestNotUsableException.class)
    public void handleClosedAsyncResponse(org.springframework.web.context.request.async.AsyncRequestNotUsableException ex) {
    }

    @ExceptionHandler(com.luminous.api.post.CommentRateLimitException.class)
    public ResponseEntity<ApiResponse<Void>> handleCommentRateLimit(com.luminous.api.post.CommentRateLimitException ex) {
        return ResponseEntity.status(ex.getStatus()).header("Retry-After", String.valueOf(ex.getRetryAfterSeconds()))
                .body(ApiResponse.fail(ex.getCode(), ex.getMessage()));
    }

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Void>> handleBusiness(BusinessException ex) {
        log.warn("Business error: {}", ex.getMessage(), ex);
        return ResponseEntity.status(ex.getStatus()).body(ApiResponse.fail(ex.getCode(), ex.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleMethodArgumentNotValid(MethodArgumentNotValidException ex) {
        log.warn("Validation error on method arguments: {}", ex.getMessage());
        String message = ex.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(err -> (err.getField() != null ? err.getField() + ": " : "") + err.getDefaultMessage())
                .orElse("请求参数不合法");
        return ResponseEntity.badRequest().body(ApiResponse.fail("VALIDATION_ERROR", message));
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiResponse<Void>> handleConstraintViolation(ConstraintViolationException ex) {
        log.warn("Constraint violation error: {}", ex.getMessage());
        String message = ex.getConstraintViolations().stream()
                .findFirst()
                .map(jakarta.validation.ConstraintViolation::getMessage)
                .orElse("请求参数不合法");
        return ResponseEntity.badRequest().body(ApiResponse.fail("VALIDATION_ERROR", message));
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ApiResponse<Void>> handleAuthentication(AuthenticationException ex) {
        log.warn("Authentication error: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(ApiResponse.fail("UNAUTHORIZED", "请先登录"));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiResponse<Void>> handleAccessDenied(AccessDeniedException ex) {
        log.warn("Access denied error: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiResponse.fail("FORBIDDEN", "无权限访问此资源"));
    }

    @ExceptionHandler(org.springframework.web.servlet.resource.NoResourceFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleMissingResource(org.springframework.web.servlet.resource.NoResourceFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse.fail("RESOURCE_NOT_FOUND", "资源不存在"));
    }

    @ExceptionHandler({org.springframework.web.method.annotation.MethodArgumentTypeMismatchException.class,
            org.springframework.http.converter.HttpMessageNotReadableException.class,
            org.springframework.web.bind.ServletRequestBindingException.class})
    public ResponseEntity<ApiResponse<Void>> handleMalformedRequest(Exception ex) {
        return ResponseEntity.badRequest().body(ApiResponse.fail("INVALID_REQUEST", "请求参数格式无效"));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleUnexpected(Exception ex) {
        log.error("Unexpected server error", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.fail("INTERNAL_ERROR", "服务器内部错误"));
    }
}
