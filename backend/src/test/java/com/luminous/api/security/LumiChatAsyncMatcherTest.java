package com.luminous.api.security;

import jakarta.servlet.DispatcherType;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import static org.assertj.core.api.Assertions.*;

/** 浏览器无法指定内部分派类型；仅结束流的内部请求获得豁免，其他接口仍需认证。 */
class LumiChatAsyncMatcherTest {
    private final LumiChatAsyncMatcher matcher=new LumiChatAsyncMatcher();
    private MockHttpServletRequest request(String method,String path,DispatcherType type){var r=new MockHttpServletRequest(method,path);r.setDispatcherType(type);return r;}
    @Test void externalInitialRequestsNeverBypassAuthentication(){
        for(var type:new DispatcherType[]{DispatcherType.REQUEST,DispatcherType.ERROR,DispatcherType.FORWARD})assertThat(matcher.matches(request("GET","/api/lumi/chat/messages/123/stream",type))).isFalse();
    }
    @Test void onlyExactReadOnlyStreamAsyncCompletionMatches(){
        assertThat(matcher.matches(request("GET","/api/lumi/chat/messages/123/stream",DispatcherType.ASYNC))).isTrue();
        for(String path:new String[]{"/api/lumi/chat","/api/lumi/chat/messages/123/stop","/api/posts/123","/api/lumi/chat/messages/123/stream/extra"})assertThat(matcher.matches(request("GET",path,DispatcherType.ASYNC))).isFalse();
        assertThat(matcher.matches(request("POST","/api/lumi/chat/messages/123/stream",DispatcherType.ASYNC))).isFalse();
    }
}
