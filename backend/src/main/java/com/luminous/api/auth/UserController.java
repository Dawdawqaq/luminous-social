package com.luminous.api.auth;

import com.luminous.api.common.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import com.luminous.api.security.CurrentUser;

@RestController
@RequestMapping("/api/users")
public class UserController {
    private final AuthService authService;

    public UserController(AuthService authService) {
        this.authService = authService;
    }

    @Operation(summary = "搜索用户")
    @GetMapping
    public ApiResponse<java.util.List<AuthDtos.UserDto>> searchUsers(
            @org.springframework.web.bind.annotation.RequestParam(required = false) String search,
            @AuthenticationPrincipal CurrentUser currentUser
    ) {
        return ApiResponse.ok(authService.searchUsers(search, currentUser));
    }

    @Operation(summary = "获取指定用户资料")
    @GetMapping("/{username}")
    public ApiResponse<AuthDtos.UserDto> getUserProfile(@PathVariable String username, @AuthenticationPrincipal CurrentUser currentUser) {
        return ApiResponse.ok(authService.getUserProfile(username, currentUser));
    }

    @Operation(summary = "关注用户")
    @PostMapping("/{username}/follow")
    public ApiResponse<AuthDtos.UserDto> follow(@PathVariable String username, @AuthenticationPrincipal CurrentUser currentUser) {
        return ApiResponse.ok(authService.follow(username, currentUser));
    }

    @Operation(summary = "取消关注")
    @DeleteMapping("/{username}/follow")
    public ApiResponse<AuthDtos.UserDto> unfollow(@PathVariable String username, @AuthenticationPrincipal CurrentUser currentUser) {
        return ApiResponse.ok(authService.unfollow(username, currentUser));
    }
}
