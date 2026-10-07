package com.luminous.api.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.luminous.api.common.ApiResponse;
import com.luminous.api.common.BusinessException;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.*;
import org.springframework.transaction.support.DefaultTransactionDefinition;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingResponseWrapper;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.*;

/** 同一操作的业务更新、发送意图和 HTTP 成功结果原子提交，超时重试重放原响应。 */
@Component
public class IdempotencyFilter extends OncePerRequestFilter {
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(IdempotencyFilter.class);
    private final IdempotencyRepository repository;
    private final PlatformTransactionManager transactions;
    private final ObjectMapper mapper;
    private final long retentionSeconds;
    public IdempotencyFilter(IdempotencyRepository repository, PlatformTransactionManager transactions, ObjectMapper mapper,
            @Value("${app.idempotency.retention-seconds:86400}") long retentionSeconds) {
        if (retentionSeconds < 60) { throw new IllegalArgumentException("幂等保留期不能少于六十秒"); }
        this.repository = repository; this.transactions = transactions; this.mapper = mapper.copy().enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS);
        this.retentionSeconds = retentionSeconds;
    }
    @Override protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith("/api/") || request.getRequestURI().startsWith("/api/auth/captcha")
                || !List.of("POST", "PUT", "DELETE", "PATCH").contains(request.getMethod())
                || request.getHeader("X-Idempotency-Key") == null;
    }
    @Override protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain) throws ServletException, IOException {
        TransactionStatus transaction = null;
        ContentCachingResponseWrapper captured = new ContentCachingResponseWrapper(response);
        try {
            String key = request.getHeader("X-Idempotency-Key");
            if (!key.matches("[a-zA-Z0-9_.:-]{8,96}")) { throw invalid("INVALID_IDEMPOTENCY_KEY", "操作标识格式无效"); }
            HttpServletRequest readable = request;
            byte[] body;
            if (request.getContentType() != null && request.getContentType().startsWith("multipart/form-data")) {
                body = multipartFingerprint(request);
            } else {
                body = request.getInputStream().readNBytes(2 * 1024 * 1024 + 1);
                if (body.length > 2 * 1024 * 1024) { throw invalid("REQUEST_TOO_LARGE", "请求体超过幂等处理限制"); }
                readable = new CachedRequest(request, body);
                if (body.length > 0 && request.getContentType() != null && request.getContentType().contains("json")) {
                    try { body = mapper.writeValueAsBytes(mapper.readValue(body, Object.class)); }
                    catch (com.fasterxml.jackson.core.JsonProcessingException ex) { throw invalid("INVALID_REQUEST_BODY", "请求体不是有效 JSON"); }
                }
            }
            String scope = scope(request);
            String fingerprint = digest(mapper.writeValueAsBytes(List.of(request.getMethod(), request.getRequestURI(),
                    Objects.toString(request.getQueryString(), ""), Objects.toString(request.getHeader("X-Captcha-Proof"), ""), digest(body))));
            DefaultTransactionDefinition definition = new DefaultTransactionDefinition(); definition.setTimeout(60);
            transaction = transactions.getTransaction(definition);
            IdempotencyRepository.Result prior = repository.claim(scope, key, fingerprint, LocalDateTime.now().plusSeconds(retentionSeconds));
            if (prior != null) {
                transactions.rollback(transaction); transaction = null;
                if (!fingerprint.equals(prior.fingerprint())) { throw conflict("IDEMPOTENCY_MISMATCH", "同一操作标识不能用于不同参数"); }
                if (!"COMPLETED".equals(prior.status())) { throw conflict("OPERATION_IN_PROGRESS", "操作处理中，请使用原标识重试"); }
                response.setStatus(prior.responseStatus()); response.setContentType(prior.contentType());
                response.setCharacterEncoding(StandardCharsets.UTF_8.name()); response.setHeader("Idempotency-Replayed", "true");
                response.getWriter().write(prior.body()); return;
            }
            readable.setAttribute(IdempotencyContext.REQUEST_ATTRIBUTE, new IdempotencyContext(
                    digest((scope + "|" + key + "|" + fingerprint).getBytes(StandardCharsets.UTF_8)), retentionSeconds));
            chain.doFilter(readable, captured);
            if (captured.getStatus() >= 400) { transactions.rollback(transaction); transaction = null; }
            else {
                repository.complete(scope, key, captured.getStatus(), captured.getContentType(),
                        new String(captured.getContentAsByteArray(), StandardCharsets.UTF_8));
                transactions.commit(transaction); transaction = null;
            }
            captured.copyBodyToResponse();
        } catch (Exception ex) {
            if (!(ex instanceof BusinessException)) { log.warn("幂等操作未确认，接口={}", request.getRequestURI(), ex); }
            if (transaction != null && !transaction.isCompleted()) { transactions.rollback(transaction); }
            if (response.isCommitted()) { throw new ServletException(ex); }
            response.resetBuffer(); response.setContentType("application/json"); response.setCharacterEncoding(StandardCharsets.UTF_8.name());
            BusinessException error = ex instanceof BusinessException business ? business
                    : new BusinessException(HttpStatus.SERVICE_UNAVAILABLE, "OPERATION_UNAVAILABLE", "操作暂未确认，请使用原操作标识重试");
            response.setStatus(error.getStatus().value()); response.getWriter().write(mapper.writeValueAsString(ApiResponse.fail(error.getCode(), error.getMessage())));
        }
    }
    private String scope(HttpServletRequest request) {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        String identity;
        if (authentication != null && authentication.getPrincipal() instanceof CurrentUser user) { identity = "user:" + user.id(); }
        else {
            String session = CaptchaSessionIdentity.find(request);
            if (session == null) { throw invalid("CAPTCHA_SESSION_MISSING", "请先获取安全验证挑战"); }
            identity = "session:" + session;
        }
        return digest((identity + "|" + request.getMethod() + "|" + request.getRequestURI()).getBytes(StandardCharsets.UTF_8));
    }
    private byte[] multipartFingerprint(HttpServletRequest request) throws Exception {
        List<Part> parts = new ArrayList<>(request.getParts());
        parts.sort(Comparator.comparing(Part::getName).thenComparing(part -> Objects.toString(part.getSubmittedFileName(), "")));
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DataOutputStream output = new DataOutputStream(bytes)) {
            for (Part part : parts) {
                output.writeUTF(part.getName()); output.writeUTF(Objects.toString(part.getSubmittedFileName(), ""));
                output.writeUTF(Objects.toString(part.getContentType(), "")); output.writeLong(part.getSize());
                MessageDigest hash = MessageDigest.getInstance("SHA-256");
                try (InputStream stream = part.getInputStream()) {
                    byte[] chunk = new byte[8192]; int read;
                    while ((read = stream.read(chunk)) != -1) { hash.update(chunk, 0, read); }
                }
                output.write(hash.digest());
            }
        }
        return bytes.toByteArray();
    }
    public static String digest(byte[] value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value)); }
        catch (java.security.NoSuchAlgorithmException ex) { throw new IllegalStateException(ex); }
    }
    private static BusinessException invalid(String code, String message) { return new BusinessException(HttpStatus.BAD_REQUEST, code, message); }
    private static BusinessException conflict(String code, String message) { return new BusinessException(HttpStatus.CONFLICT, code, message); }
    private static final class CachedRequest extends HttpServletRequestWrapper {
        private final byte[] body;
        private CachedRequest(HttpServletRequest request, byte[] body) { super(request); this.body = body; }
        @Override public ServletInputStream getInputStream() {
            ByteArrayInputStream input = new ByteArrayInputStream(body);
            return new ServletInputStream() {
                @Override public int read() { return input.read(); }
                @Override public boolean isFinished() { return input.available() == 0; }
                @Override public boolean isReady() { return true; }
                @Override public void setReadListener(ReadListener listener) { throw new UnsupportedOperationException("当前写接口采用同步处理"); }
            };
        }
        @Override public BufferedReader getReader() { return new BufferedReader(new InputStreamReader(getInputStream(), StandardCharsets.UTF_8)); }
    }
}
