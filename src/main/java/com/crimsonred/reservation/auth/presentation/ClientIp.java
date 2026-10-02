package com.crimsonred.reservation.auth.presentation;

import jakarta.servlet.http.HttpServletRequest;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class ClientIp {
  private final String proxySecret;

  public ClientIp(@Value("${app.proxy-secret:}") String proxySecret) {
    this.proxySecret = proxySecret;
  }

  public String address(HttpServletRequest request) {
    String key = request.getHeader("X-Crimson-Proxy-Key"),
        ip = request.getHeader("X-Crimson-Client-IP");
    if (!proxySecret.isBlank()
        && key != null
        && ip != null
        && ip.matches("[0-9a-fA-F:.]{3,45}")
        && MessageDigest.isEqual(
            proxySecret.getBytes(StandardCharsets.UTF_8), key.getBytes(StandardCharsets.UTF_8)))
      return ip;
    return request.getRemoteAddr();
  }
}
