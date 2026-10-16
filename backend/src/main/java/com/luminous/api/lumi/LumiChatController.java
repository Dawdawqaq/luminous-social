package com.luminous.api.lumi;

import com.luminous.api.common.ApiResponse;
import com.luminous.api.security.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

/** 私有聊天只使用认证主体编号，不接受客户端指定其他账号。 */
@RestController
@Validated
@RequestMapping("/api/lumi/chat")
public class LumiChatController {
    private final LumiChatService service;
    private final LumiChatStreams streams;
    public LumiChatController(LumiChatService service,LumiChatStreams streams){this.service=service;this.streams=streams;}
    @GetMapping public ApiResponse<LumiChatDtos.Page> read(@AuthenticationPrincipal CurrentUser user,@RequestParam(required=false) @Positive Long sessionId,@RequestParam(required=false) @Positive Long before){return ApiResponse.ok(sessionId==null?service.read(user.id(),before):service.read(user.id(),sessionId,before));}
    @GetMapping("/conversations") public ApiResponse<LumiChatDtos.Conversations> conversations(@AuthenticationPrincipal CurrentUser user,@RequestParam(required=false) String cursor){return ApiResponse.ok(service.list(user.id(),cursor));}
    @PostMapping("/conversations") public ApiResponse<LumiChatDtos.Page> create(@AuthenticationPrincipal CurrentUser user,@Valid @RequestBody LumiChatDtos.Create request){return ApiResponse.ok(service.create(user.id(),request.requestKey()));}
    @PutMapping("/conversations/{id}") public ApiResponse<Void> rename(@AuthenticationPrincipal CurrentUser user,@PathVariable @Positive long id,@Valid @RequestBody LumiChatDtos.Rename request){service.rename(user.id(),id,request.title());return ApiResponse.ok(null);}
    @DeleteMapping("/conversations/{id}") public ApiResponse<Void> delete(@AuthenticationPrincipal CurrentUser user,@PathVariable @Positive long id){service.delete(user.id(),id);return ApiResponse.ok(null);}
    @PostMapping("/messages") public ApiResponse<LumiChatDtos.Turn> send(@AuthenticationPrincipal CurrentUser user,@Valid @RequestBody LumiChatDtos.Send request){return ApiResponse.ok(service.send(user.id(),request));}
    @PostMapping("/reset") public ApiResponse<LumiChatDtos.Page> reset(@AuthenticationPrincipal CurrentUser user,@Valid @RequestBody LumiChatDtos.Reset request){return ApiResponse.ok(service.reset(user.id(),request.sessionId()));}
    @PostMapping("/messages/{id}/retry") public ApiResponse<LumiChatDtos.Turn> retry(@AuthenticationPrincipal CurrentUser user,@PathVariable long id){return ApiResponse.ok(service.retry(id,user.id()));}
    @PostMapping("/messages/{id}/stop") public ApiResponse<LumiChatDtos.Turn> stop(@AuthenticationPrincipal CurrentUser user,@PathVariable long id){return ApiResponse.ok(service.stop(user.id(),id));}
    @GetMapping("/messages/{id}/stream")
    public org.springframework.http.ResponseEntity<org.springframework.web.servlet.mvc.method.annotation.SseEmitter> stream(@AuthenticationPrincipal CurrentUser user,@PathVariable @Positive long id){
        return org.springframework.http.ResponseEntity.ok().contentType(org.springframework.http.MediaType.TEXT_EVENT_STREAM).header("Cache-Control","no-store").header("X-Accel-Buffering","no").body(streams.open(user.id(),id));
    }
}
