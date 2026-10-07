package com.luminous.api.auth;

import com.luminous.api.common.ApiResponse;
import com.luminous.api.security.CurrentUser;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final AuthService authService;
    private final com.luminous.api.security.CaptchaService captcha;

    public AuthController(AuthService authService, com.luminous.api.security.CaptchaService captcha) {
        this.authService = authService;
        this.captcha = captcha;
    }

    @Operation(summary = "注册账号")
    @com.luminous.api.security.Idempotent
    @PostMapping("/register")
    public ApiResponse<AuthDtos.AuthResponse> register(@Valid @RequestBody AuthDtos.RegisterRequest request,
            jakarta.servlet.http.HttpServletRequest servletRequest, @org.springframework.web.bind.annotation.RequestHeader(value = "X-Captcha-Proof", required = false) String proof) {
        captcha.consume(com.luminous.api.security.CaptchaSessionIdentity.find(servletRequest), "register", proof,
                (com.luminous.api.security.IdempotencyContext) servletRequest.getAttribute(com.luminous.api.security.IdempotencyContext.REQUEST_ATTRIBUTE));
        return ApiResponse.ok(authService.register(request));
    }

    @Operation(summary = "登录并获取 JWT")
    @com.luminous.api.security.Idempotent
    @PostMapping("/login")
    public ApiResponse<AuthDtos.AuthResponse> login(@Valid @RequestBody AuthDtos.LoginRequest request,
            jakarta.servlet.http.HttpServletRequest servletRequest, @org.springframework.web.bind.annotation.RequestHeader(value = "X-Captcha-Proof", required = false) String proof) {
        captcha.consume(com.luminous.api.security.CaptchaSessionIdentity.find(servletRequest), "login", proof,
                (com.luminous.api.security.IdempotencyContext) servletRequest.getAttribute(com.luminous.api.security.IdempotencyContext.REQUEST_ATTRIBUTE));
        return ApiResponse.ok(authService.login(request));
    }

    @Operation(summary = "获取当前登录用户")
    @GetMapping("/me")
    public ApiResponse<AuthDtos.UserDto> me(@AuthenticationPrincipal CurrentUser currentUser) {
        return ApiResponse.ok(authService.currentUser(currentUser));
    }

    @Operation(summary = "更新当前登录用户资料")
    @org.springframework.web.bind.annotation.PutMapping("/me")
    public ApiResponse<AuthDtos.UserDto> updateProfile(@RequestBody AuthDtos.UpdateProfileRequest request, @AuthenticationPrincipal CurrentUser currentUser) {
        return ApiResponse.ok(authService.updateProfile(request, currentUser));
    }
}
