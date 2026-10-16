package com.luminous.api.community;

import com.luminous.api.common.ApiResponse;
import com.luminous.api.common.BusinessException;
import com.luminous.api.security.CurrentUser;
import org.springframework.http.HttpStatus;
import io.swagger.v3.oas.annotations.Operation;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/communities")
public class CommunityController {
    private final CommunityService communityService;

    public CommunityController(CommunityService communityService) {
        this.communityService = communityService;
    }

    @Operation(summary = "获取社区列表")
    @GetMapping
    public ApiResponse<List<CommunityDtos.CommunityDto>> list(
            @RequestParam(required = false) String search,
            @AuthenticationPrincipal CurrentUser currentUser
    ) {
        return ApiResponse.ok(communityService.list(search, currentUser));
    }

    @Operation(summary = "获取推荐社区")
    @GetMapping("/recommended")
    public ApiResponse<List<CommunityDtos.RecommendedCommunityDto>> recommended(@AuthenticationPrincipal CurrentUser currentUser) {
        return ApiResponse.ok(communityService.recommended(currentUser));
    }

    @Operation(summary = "获取社区详情")
    @GetMapping("/{slug}")
    public ApiResponse<CommunityDtos.CommunityDto> detail(@PathVariable String slug, @AuthenticationPrincipal CurrentUser currentUser) {
        return ApiResponse.ok(communityService.detail(slug, currentUser));
    }

    @Operation(summary = "加入社区")
    @PostMapping("/{slug}/join")
    public ApiResponse<CommunityDtos.CommunityDto> join(@PathVariable String slug, @AuthenticationPrincipal CurrentUser currentUser) {
        return ApiResponse.ok(communityService.join(slug, currentUser));
    }

    @Operation(summary = "退出社区")
    @DeleteMapping("/{slug}/join")
    public ApiResponse<CommunityDtos.CommunityDto> leave(@PathVariable String slug, @AuthenticationPrincipal CurrentUser currentUser) {
        return ApiResponse.ok(communityService.leave(slug, currentUser));
    }

    @Operation(summary = "创建社区")
    @PostMapping
    public ApiResponse<CommunityDtos.CommunityDto> create(@Valid @RequestBody CommunityDtos.CreateCommunityRequest request, @AuthenticationPrincipal CurrentUser currentUser) {
        if (currentUser == null) throw new BusinessException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "请先登录");
        return ApiResponse.ok(communityService.create(request, currentUser));
    }

    @Operation(summary = "更新社区")
    @PutMapping("/{slug}")
    public ApiResponse<CommunityDtos.CommunityDto> update(@PathVariable String slug, @Valid @RequestBody CommunityDtos.UpdateCommunityRequest request, @AuthenticationPrincipal CurrentUser currentUser) {
        if (currentUser == null) throw new BusinessException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "请先登录");
        return ApiResponse.ok(communityService.update(slug, request, currentUser));
    }
}
