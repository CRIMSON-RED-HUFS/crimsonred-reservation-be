package com.crimsonred.reservation.shared.domain;

public class BusinessException extends RuntimeException {
  public enum Type {
    INVALID_INPUT,
    UNAUTHENTICATED,
    FORBIDDEN,
    NOT_FOUND,
    CONFLICT,
    RATE_LIMITED,
    UNAVAILABLE
  }

  public final Type type;
  public final String code;

  public BusinessException(Type type, String code, String message) {
    super(message);
    this.type = type;
    this.code = code;
  }

  public static BusinessException bad(String message) {
    return new BusinessException(Type.INVALID_INPUT, "INVALID_INPUT", message);
  }

  public static BusinessException conflict(String code, String message) {
    return new BusinessException(Type.CONFLICT, code, message);
  }

  public static BusinessException missing() {
    return new BusinessException(Type.NOT_FOUND, "NOT_FOUND", "대상을 찾을 수 없습니다.");
  }
}
