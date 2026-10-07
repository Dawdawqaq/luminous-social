package com.luminous.api.auth;

import com.luminous.api.common.ApiResponse;
import com.luminous.api.security.*;
import jakarta.servlet.http.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth/captcha")
public class CaptchaController {
    public record VerifyRequest(@NotBlank String purpose, @NotBlank String challengeId,
                                @Min(0) int offset, @Min(250) @Max(60000) long durationMillis) {}
    private final CaptchaService captcha;
    private final boolean secureCookies;
    public CaptchaController(CaptchaService captcha, @Value("${app.security.secure-cookies:false}") boolean secureCookies) {
        this.captcha = captcha; this.secureCookies = secureCookies;
    }
    @GetMapping public ApiResponse<CaptchaService.Challenge> challenge(@RequestParam String purpose,
            HttpServletRequest request, HttpServletResponse response) {
        response.setHeader("Cache-Control", "no-store");
        return ApiResponse.ok(captcha.create(CaptchaSessionIdentity.resolve(request, response, secureCookies), purpose));
    }
    @PostMapping("/verify") public ApiResponse<CaptchaService.Verification> verify(@Valid @RequestBody VerifyRequest body,
            HttpServletRequest request, HttpServletResponse response) {
        response.setHeader("Cache-Control", "no-store");
        return ApiResponse.ok(captcha.verify(CaptchaSessionIdentity.find(request), body.purpose(), body.challengeId(), body.offset(), body.durationMillis()));
    }
}
