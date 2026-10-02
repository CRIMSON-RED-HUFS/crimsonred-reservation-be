package com.crimsonred.reservation.auth.presentation;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

class ClientIpTest {
  @Test
  void untrustedHeadersCannotChangeTheRateLimitAddress() {
    var request = new MockHttpServletRequest();
    request.setRemoteAddr("127.0.0.1");
    request.addHeader("X-Forwarded-For", "203.0.113.10");
    request.addHeader("X-Crimson-Client-IP", "203.0.113.11");
    request.addHeader("X-Crimson-Proxy-Key", "wrong");
    assertEquals("127.0.0.1", new ClientIp("private-test-key").address(request));
    request.removeHeader("X-Crimson-Proxy-Key");
    request.addHeader("X-Crimson-Proxy-Key", "private-test-key");
    assertEquals("203.0.113.11", new ClientIp("private-test-key").address(request));
    assertEquals("127.0.0.1", new ClientIp("").address(request));
    request.removeHeader("X-Crimson-Client-IP");
    request.addHeader("X-Crimson-Client-IP", "spoof.example");
    assertEquals("127.0.0.1", new ClientIp("private-test-key").address(request));
  }
}
