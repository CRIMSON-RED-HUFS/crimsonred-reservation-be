package com.crimsonred.reservation.auth.presentation;

import com.crimsonred.reservation.shared.domain.BusinessException;
import org.springframework.security.core.Authentication;

public final class CurrentMember {
  private CurrentMember() {}

  public static Long id(Authentication authentication) {
    if (authentication == null || !(authentication.getPrincipal() instanceof Long id))
      throw new BusinessException(
          BusinessException.Type.UNAUTHENTICATED, "UNAUTHENTICATED", "로그인이 필요합니다.");
    return id;
  }
}
