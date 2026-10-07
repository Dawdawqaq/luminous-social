package com.luminous.api.mq;

import com.luminous.api.common.ApiResponse;
import com.luminous.api.security.CurrentUser;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.util.List;

/** 管理员查看与补发已持久化的失败消息。 */
@RestController
@RequestMapping("/api/admin/mq/failures")
public class FailedMessageController {
    private final FailedMessageService failures;
    public FailedMessageController(FailedMessageService failures) { this.failures = failures; }

    @GetMapping
    public ApiResponse<List<FailedMessageDtos.FailureDto>> list(@RequestParam(defaultValue="1") int page,
            @RequestParam(defaultValue="20") int size, @AuthenticationPrincipal CurrentUser user) {
        return ApiResponse.ok(failures.list(page, size, user));
    }
    @PostMapping("/{id}/replay")
    public ApiResponse<Void> replay(@PathVariable long id, @AuthenticationPrincipal CurrentUser user) {
        failures.replay(id, user); return ApiResponse.ok(null);
    }
}
