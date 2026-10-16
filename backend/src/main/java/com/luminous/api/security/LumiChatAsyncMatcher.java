package com.luminous.api.security;

import jakarta.servlet.DispatcherType;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.web.util.matcher.RequestMatcher;

/** 只放行已认证流请求结束时的内部异步分派；外部首次请求仍执行完整认证和归属检查。 */
public final class LumiChatAsyncMatcher implements RequestMatcher {
    @Override public boolean matches(HttpServletRequest request){
        return request.getDispatcherType()==DispatcherType.ASYNC
                &&"GET".equals(request.getMethod())
                &&request.getRequestURI().matches("/api/lumi/chat/messages/[0-9]+/stream");
    }
}
