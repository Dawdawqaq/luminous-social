package com.luminous.api.feed;

import com.luminous.api.common.ApiResponse;
import com.luminous.api.common.BusinessException;
import com.luminous.api.post.PostDtos;
import com.luminous.api.security.CurrentUser;
import io.swagger.v3.oas.annotations.Operation;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/feed")
public class FeedController {
    private final FeedService feedService;
    
    public FeedController(FeedService feedService) {
        this.feedService = feedService;
    }
    
    @Operation(summary = "获取关注用户的 Feed 流（滚动分页）")
    @GetMapping
    public ApiResponse<FeedDtos.ScrollResult<PostDtos.PostDto>> feed(
        @RequestParam(required = false) Long lastScore,
        @RequestParam(defaultValue = "0") int offset,
        @RequestParam(defaultValue = "10") int size,
        @AuthenticationPrincipal CurrentUser currentUser
    ) {
        if (currentUser == null) {
            throw new BusinessException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "请先登录");
        }
        return ApiResponse.ok(feedService.scrollFeed(currentUser, lastScore, offset, size));
    }
}
