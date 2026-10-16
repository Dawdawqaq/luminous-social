package com.luminous.api.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.transaction.PlatformTransactionManager;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/** 聊天有自身持久幂等规则，不得把私有响应复制到通用缓存；其他写入口不受影响。 */
class IdempotencyFilterScopeTest {
    final IdempotencyFilter filter=new IdempotencyFilter(mock(IdempotencyRepository.class),mock(PlatformTransactionManager.class),new ObjectMapper(),86400);
    MockHttpServletRequest request(String path){var request=new MockHttpServletRequest("POST",path);request.addHeader("X-Idempotency-Key","E2E_TEST_12345678");return request;}
    @Test void privateChatEndpointsSkipGenericResponseStorage(){for(String path:new String[]{"/api/lumi/chat/messages","/api/lumi/chat/reset","/api/lumi/chat/messages/123/retry"})assertThat(filter.shouldNotFilter(request(path))).isTrue();}
    @Test void postAssistantAndSimilarPrefixesRetainExistingIdempotency(){for(String path:new String[]{"/api/posts/123/comments","/api/lumi/tasks/123/retry","/api/lumi/chatter/messages"})assertThat(filter.shouldNotFilter(request(path))).isFalse();}
}
