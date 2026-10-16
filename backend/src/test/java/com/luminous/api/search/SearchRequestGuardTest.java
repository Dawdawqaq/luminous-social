package com.luminous.api.search;

import com.luminous.api.common.BusinessException;
import com.luminous.api.security.*;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import java.time.Duration;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static org.assertj.core.api.Assertions.*;

class SearchRequestGuardTest {
    @Test void anonymousAndSignedInLimitsUseTrustedSourceAndSeparateActorKeys() {
        var limiter = mock(SlidingWindowRateLimiter.class);
        when(limiter.isAllowed(anyString(), anyInt(), any())).thenReturn(true);
        var guard = new SearchRequestGuard(limiter, new ClientIpResolver(""));
        var request = new MockHttpServletRequest(); request.setRemoteAddr("127.0.0.1"); request.addHeader("X-Forwarded-For", "8.8.8.8");
        guard.check(null, request); guard.check(new CurrentUser(7L, "E2E_TEST", "USER"), request);
        verify(limiter, times(2)).isAllowed(startsWith("luminous:search:limit:ip:"), eq(300), eq(Duration.ofMinutes(1)));
        verify(limiter).isAllowed(startsWith("luminous:search:limit:anonymous:"), eq(60), any());
        verify(limiter).isAllowed(eq("luminous:search:limit:user:7"), eq(120), any());
    }
    @Test void limitCannotBeBypassedWithDevHeader() {
        var limiter = mock(SlidingWindowRateLimiter.class);
        var guard = new SearchRequestGuard(limiter, new ClientIpResolver(""));
        var request = new MockHttpServletRequest(); request.setRemoteAddr("127.0.0.1"); request.addHeader("X-Bypass-Rate-Limit", "true");
        assertThatThrownBy(() -> guard.check(null, request)).isInstanceOf(BusinessException.class).satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo("SEARCH_RATE_LIMITED"));
        verify(limiter, times(1)).isAllowed(anyString(), anyInt(), any());
    }
}
