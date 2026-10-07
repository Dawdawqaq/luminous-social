package com.luminous.api.security;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.ResponseCookie;
import java.util.UUID;

/** 浏览器会话绑定验证码与匿名认证幂等范围，不接受客户端自报的用户 ID。 */
public final class CaptchaSessionIdentity {
    public static final String COOKIE = "luminous_captcha_session";
    private CaptchaSessionIdentity() {}
    public static String find(HttpServletRequest request) {
        if (request.getCookies() != null) {
            for (Cookie cookie : request.getCookies()) {
                if (COOKIE.equals(cookie.getName()) && cookie.getValue().matches("[a-f0-9]{32}")) { return cookie.getValue(); }
            }
        }
        return null;
    }
    public static String resolve(HttpServletRequest request, HttpServletResponse response, boolean secure) {
        String session = find(request);
        if (session == null) {
            session = UUID.randomUUID().toString().replace("-", "");
            response.addHeader("Set-Cookie", ResponseCookie.from(COOKIE, session).httpOnly(true).secure(secure || request.isSecure())
                    .sameSite("Lax").path("/").build().toString());
        }
        return session;
    }
}
