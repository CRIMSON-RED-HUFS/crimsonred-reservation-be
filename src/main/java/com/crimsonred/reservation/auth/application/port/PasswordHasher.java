package com.crimsonred.reservation.auth.application.port;

public interface PasswordHasher {
  String encode(CharSequence password);

  boolean matches(CharSequence password, String encoded);
}
