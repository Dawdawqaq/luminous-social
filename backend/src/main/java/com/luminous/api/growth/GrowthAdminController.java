package com.luminous.api.growth;

import com.luminous.api.common.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin")
@PreAuthorize("hasAuthority('ADMIN')")
public class GrowthAdminController {
    private final GrowthModerationService moderation;
    public GrowthAdminController(GrowthModerationService moderation) { this.moderation = moderation; }
    @Operation(summary = "封禁违规帖子并撤销实际奖励，已删除帖子按留存来源追责")
    @PostMapping("/posts/{id}/ban")
    public ApiResponse<Void> ban(@PathVariable long id, @Valid @RequestBody GrowthDtos.ModerationRequest request) {
        moderation.banPost(id, request.reason());
        return ApiResponse.ok(null);
    }

    @Operation(summary = "撤销违规评论的实际奖励，支持评论或父帖子已删除后的追责")
    @PostMapping("/comments/{id}/revoke-growth")
    public ApiResponse<Void> revokeComment(@PathVariable long id, @Valid @RequestBody GrowthDtos.ModerationRequest request) {
        moderation.revokeComment(id, request.reason());
        return ApiResponse.ok(null);
    }
}
