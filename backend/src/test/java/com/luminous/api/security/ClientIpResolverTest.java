package com.luminous.api.security;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import static org.assertj.core.api.Assertions.*;

class ClientIpResolverTest {
    @Test void untrustedPeerCannotForgeForwardedHeaders() {
        MockHttpServletRequest request = new MockHttpServletRequest(); request.setRemoteAddr("198.51.100.7");
        request.addHeader("X-Forwarded-For", "203.0.113.9"); request.addHeader("X-Real-IP", "203.0.113.10");
        assertThat(new ClientIpResolver("172.30.50.10/32").resolve(request)).isEqualTo("198.51.100.7");
    }
    @Test void proxyChainUsesNearestUntrustedAddressAndRejectsMalformedValues() {
        MockHttpServletRequest request = new MockHttpServletRequest(); request.setRemoteAddr("172.30.50.10");
        request.addHeader("X-Forwarded-For", "203.0.113.99,198.51.100.7");
        ClientIpResolver resolver = new ClientIpResolver("172.30.50.10/32");
        assertThat(resolver.resolve(request)).isEqualTo("198.51.100.7");
        request.removeHeader("X-Forwarded-For"); request.addHeader("X-Forwarded-For", "untrusted.example");
        assertThat(resolver.resolve(request)).isEqualTo("172.30.50.10");
    }
}
