package com.luminous.api.ranking;

import com.luminous.api.common.ApiResponse;
import com.luminous.api.security.CurrentUser;
import io.swagger.v3.oas.annotations.Operation;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/posts/ranking")
public class PostRankingController {
    private final PostRankingService rankings;
    public PostRankingController(PostRankingService rankings) { this.rankings = rankings; }

    @Operation(summary = "推荐及日周热榜，使用短期排序快照分页")
    @GetMapping
    public ApiResponse<PostRankingService.Slice> list(@RequestParam(defaultValue = "recommend") String type,
            @RequestParam(defaultValue = "day") String period, @RequestParam(required = false) String community,
            @RequestParam(required = false) String cursor, @RequestParam(defaultValue = "10") int size,
            @AuthenticationPrincipal CurrentUser viewer) {
        return ApiResponse.ok(rankings.list(type, period, community, cursor, size, viewer));
    }
}
