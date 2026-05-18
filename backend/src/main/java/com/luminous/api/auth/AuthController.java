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

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @Operation(summary = "注册账号")
    @PostMapping("/register")
    public ApiResponse<AuthDtos.AuthResponse> register(@Valid @RequestBody AuthDtos.RegisterRequest request) {
        return ApiResponse.ok(authService.register(request));
    }

    @Operation(summary = "登录并获取 JWT")
    @PostMapping("/login")
    public ApiResponse<AuthDtos.AuthResponse> login(@Valid @RequestBody AuthDtos.LoginRequest request) {
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
