package com.crimsonred.reservation.auth.application.port;

public interface AuthRateLimiter {
  void login(String ip, String email);

  void mail(String ip, String email);
}
