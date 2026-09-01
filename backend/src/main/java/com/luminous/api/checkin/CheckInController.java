package com.luminous.api.checkin;

import com.luminous.api.common.ApiResponse;
import com.luminous.api.security.CurrentUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@Tag(name = "签到")
@RestController
@RequestMapping("/api/checkin")
public class CheckInController {

    private final CheckInService checkInService;

    public CheckInController(CheckInService checkInService) {
        this.checkInService = checkInService;
    }
    
    @Operation(summary = "社区签到")
    @PostMapping("/{communitySlug}")
    public ApiResponse<CheckInDtos.CheckInResponse> checkIn(
        @PathVariable String communitySlug,
        @AuthenticationPrincipal CurrentUser currentUser
    ) {
        return ApiResponse.ok(checkInService.checkIn(communitySlug, currentUser));
    }
    
    @Operation(summary = "获取签到统计")
    @GetMapping("/{communitySlug}/stats")
    public ApiResponse<CheckInDtos.CheckInStats> stats(
        @PathVariable String communitySlug,
        @AuthenticationPrincipal CurrentUser currentUser
    ) {
        return ApiResponse.ok(checkInService.getStats(communitySlug, currentUser));
    }
}
