package com.luminous.api.growth;

import com.luminous.api.common.ApiResponse;
import com.luminous.api.common.PageResponse;
import com.luminous.api.security.CurrentUser;
import io.swagger.v3.oas.annotations.Operation;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
public class GrowthController {
    private final GrowthQueryService queries;
    private final GrowthReadService reads;
    public GrowthController(GrowthQueryService queries, GrowthReadService reads) { this.queries = queries; this.reads = reads; }
    @Operation(summary = "获取用户个人与指定社区的真实等级和进度")
    @GetMapping("/users/{username}/growth")
    public ApiResponse<GrowthDtos.Overview> overview(@PathVariable String username, @RequestParam(required = false) String community) {
        return ApiResponse.ok(queries.overview(username, community));
    }
    @Operation(summary = "分页查看自己的成长奖励与扣回流水")
    @GetMapping("/growth/ledger")
    public ApiResponse<PageResponse<GrowthDtos.LedgerEntry>> ledger(@AuthenticationPrincipal CurrentUser user,
            @RequestParam(defaultValue = "1") long page, @RequestParam(defaultValue = "20") long size) {
        return ApiResponse.ok(queries.ledger(user, page, size));
    }
    @Operation(summary = "创建受控阅读会话，资格规则启用后可用")
    @PostMapping("/posts/{id}/read-session")
    public ApiResponse<GrowthDtos.ReadSession> begin(@PathVariable long id, @AuthenticationPrincipal CurrentUser user) {
        return ApiResponse.ok(reads.begin(id, user));
    }
    @Operation(summary = "完成受控阅读会话，同一读者同一帖子不重复计经验")
    @PostMapping("/posts/{id}/read-session/{token}/complete")
    public ApiResponse<GrowthDtos.ReadResult> complete(@PathVariable long id, @PathVariable String token,
            @AuthenticationPrincipal CurrentUser user) {
        return ApiResponse.ok(reads.complete(id, token, user));
    }
}
