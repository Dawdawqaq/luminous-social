package com.luminous.api.lumi;

import com.luminous.api.common.ApiResponse;
import com.luminous.api.security.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/** 请求资格由认证及任务服务共同检查，不提供客户端指定机器人作者或发布位置的入口。 */
@RestController
@RequestMapping("/api/lumi")
public class LumiController {
    private final LumiTaskService tasks;
    public LumiController(LumiTaskService tasks){this.tasks=tasks;}
    @GetMapping("/posts/{postId}/tasks")
    public ApiResponse<LumiDtos.Status> status(@PathVariable long postId,@AuthenticationPrincipal CurrentUser user){return ApiResponse.ok(tasks.status(postId,user.id()));}
    @PostMapping("/tasks/{id}/retry")
    @Idempotent
    public ApiResponse<LumiDtos.Task> retry(@PathVariable long id,@AuthenticationPrincipal CurrentUser user){return ApiResponse.ok(tasks.retry(id,user.id()));}
}
