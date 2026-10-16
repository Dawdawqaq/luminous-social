package com.luminous.api.community;

import com.luminous.api.common.ApiResponse;
import com.luminous.api.security.CurrentUser;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/communities/browse")
public class CommunityBrowseController {
    private final CommunityBrowseService service;
    public CommunityBrowseController(CommunityBrowseService service) { this.service = service; }
    @GetMapping
    public ApiResponse<CommunityBrowseService.Slice> list(@RequestParam(required = false) String search,
            @RequestParam(required = false) String category, @RequestParam(defaultValue = "all") String scope,
            @RequestParam(required = false) String cursor, @RequestParam(defaultValue = "12") int size,
            @AuthenticationPrincipal CurrentUser user) {
        return ApiResponse.ok(service.list(search, category, scope, cursor, size, user));
    }
}
