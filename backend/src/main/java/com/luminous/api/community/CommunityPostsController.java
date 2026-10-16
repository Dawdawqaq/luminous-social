package com.luminous.api.community;

import com.luminous.api.common.ApiResponse;
import com.luminous.api.security.CurrentUser;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/communities/{slug}/posts")
public class CommunityPostsController {
    private final CommunityPostsService service;
    public CommunityPostsController(CommunityPostsService service) { this.service = service; }
    @GetMapping public ApiResponse<CommunityPostsService.Slice> list(@PathVariable String slug,
            @RequestParam(required = false) String cursor, @RequestParam(defaultValue = "10") int size,
            @AuthenticationPrincipal CurrentUser viewer) {
        return ApiResponse.ok(service.list(slug, cursor, size, viewer));
    }
}
