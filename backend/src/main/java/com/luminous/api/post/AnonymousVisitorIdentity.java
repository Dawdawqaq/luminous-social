package com.luminous.api.post;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.ResponseCookie;
import java.time.Duration;
import java.util.UUID;

/** 使用浏览器持久 Cookie 标识匿名身份；不以请求次数或 IP 代替用户身份。 */
public final class AnonymousVisitorIdentity {
    public static final String COOKIE_NAME = "luminous_visitor";
    private AnonymousVisitorIdentity() {}
    public static String resolve(HttpServletRequest request, HttpServletResponse response) {
        if (request.getCookies() != null) {
            for (Cookie cookie : request.getCookies()) {
                if (COOKIE_NAME.equals(cookie.getName()) && cookie.getValue().matches("[a-f0-9]{32}")) {
                    return cookie.getValue();
                }
            }
        }
        String visitor = UUID.randomUUID().toString().replace("-", "");
        response.addHeader("Set-Cookie", ResponseCookie.from(COOKIE_NAME, visitor)
                .httpOnly(true).secure(request.isSecure()).sameSite("Lax").path("/")
                .maxAge(Duration.ofDays(365)).build().toString());
        return visitor;
    }
}
