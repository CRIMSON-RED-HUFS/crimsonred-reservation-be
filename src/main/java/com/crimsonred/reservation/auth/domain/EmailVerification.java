package com.crimsonred.reservation.auth.domain;

import com.crimsonred.reservation.shared.domain.BusinessException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;

public class EmailVerification {
  private Long id;
  private Long memberId;
  private String tokenHash;
  private Instant expiresAt;
  private Instant usedAt;

  public Long getId() {
    return id;
  }

  public Long getMemberId() {
    return memberId;
  }

  public Instant getExpiresAt() {
    return expiresAt;
  }

  public Instant getUsedAt() {
    return usedAt;
  }

  public void renew(Long memberId, String tokenHash, Instant expiresAt) {
    this.memberId = memberId;
    this.tokenHash = tokenHash;
    this.expiresAt = expiresAt;
    this.usedAt = null;
  }

  public void consume(String suppliedHash, Instant now) {
    if (!MessageDigest.isEqual(
            tokenHash.getBytes(StandardCharsets.US_ASCII),
            suppliedHash.getBytes(StandardCharsets.US_ASCII))
        || usedAt != null
        || !now.isBefore(expiresAt)) throw BusinessException.bad("만료되었거나 이미 사용한 인증 링크입니다.");
    usedAt = now;
  }
}
