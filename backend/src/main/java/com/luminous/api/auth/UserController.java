package com.luminous.api.auth;

import com.luminous.api.common.ApiResponse;
import com.luminous.api.common.PageResponse;
import org.springframework.web.bind.annotation.RequestParam;
import io.swagger.v3.oas.annotations.Operation;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import com.luminous.api.security.CurrentUser;

/** 用户标识默认兼容旧查找；identifierType=username/id 可显式选择命名空间。 */
@RestController
@RequestMapping("/api/users")
public class UserController {
    private final AuthService authService;

    public UserController(AuthService authService) {
        this.authService = authService;
    }

    @Operation(summary = "获取内置头像与主页背景预设")
    @GetMapping("/profile-presets")
    public ApiResponse<ProfilePresetCatalog.Catalog> profilePresets() {
        return ApiResponse.ok(ProfilePresetCatalog.catalog());
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
    public ApiResponse<AuthDtos.UserDto> getUserProfile(@PathVariable String username,
            @RequestParam(defaultValue = "auto") String identifierType, @AuthenticationPrincipal CurrentUser currentUser) {
        return ApiResponse.ok(authService.getUserProfile(username, currentUser, UserIdentifierType.parse(identifierType)));
    }

    @Operation(summary = "关注用户")
    @PostMapping("/{username}/follow")
    @com.luminous.api.security.Idempotent
    public ApiResponse<AuthDtos.UserDto> follow(@PathVariable String username,
            @RequestParam(defaultValue = "auto") String identifierType, @AuthenticationPrincipal CurrentUser currentUser) {
        return ApiResponse.ok(authService.follow(username, currentUser, UserIdentifierType.parse(identifierType)));
    }

    @Operation(summary = "分页获取用户粉丝列表")
    @GetMapping("/{username}/followers")
    public ApiResponse<PageResponse<AuthDtos.FollowUserDto>> followers(
            @PathVariable String username,
            @RequestParam(defaultValue = "auto") String identifierType,
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "20") long size,@AuthenticationPrincipal CurrentUser viewer) {
        return ApiResponse.ok(authService.listFollowers(username, page, size, viewer, UserIdentifierType.parse(identifierType)));
    }

    @Operation(summary = "分页获取用户关注列表")
    @GetMapping("/{username}/following")
    public ApiResponse<PageResponse<AuthDtos.FollowUserDto>> following(
            @PathVariable String username,
            @RequestParam(defaultValue = "auto") String identifierType,
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "20") long size,@AuthenticationPrincipal CurrentUser viewer) {
        return ApiResponse.ok(authService.listFollowing(username, page, size, viewer, UserIdentifierType.parse(identifierType)));
    }

    @Operation(summary = "取消关注")
    @DeleteMapping("/{username}/follow")
    @com.luminous.api.security.Idempotent
    public ApiResponse<AuthDtos.UserDto> unfollow(@PathVariable String username,
            @RequestParam(defaultValue = "auto") String identifierType, @AuthenticationPrincipal CurrentUser currentUser) {
        return ApiResponse.ok(authService.unfollow(username, currentUser, UserIdentifierType.parse(identifierType)));
    }
}
