package com.crimsonred.reservation.auth.infrastructure.security;

import com.crimsonred.reservation.auth.application.port.PasswordHasher;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
public class Argon2PasswordHasher implements PasswordHasher {
  private final PasswordEncoder encoder;

  public Argon2PasswordHasher(PasswordEncoder encoder) {
    this.encoder = encoder;
  }

  public String encode(CharSequence password) {
    return encoder.encode(password);
  }

  public boolean matches(CharSequence password, String encoded) {
    return encoder.matches(password, encoded);
  }
}
