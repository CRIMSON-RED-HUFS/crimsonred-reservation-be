package com.crimsonred.reservation.auth.domain;

import com.crimsonred.reservation.shared.domain.BusinessException;
import java.util.regex.Pattern;

public final class PasswordPolicy {
  public static final String SIGNUP_PATTERN =
      "(?=.*[A-Za-z])(?=.*[0-9])(?=.*[^A-Za-z0-9])[\\x21-\\x7E]{8,128}";
  public static final String SIGNUP_MESSAGE = "비밀번호는 공백 없이 8~128자, 영문·숫자·특수문자를 모두 포함해주세요.";
  private static final Pattern SIGNUP = Pattern.compile(SIGNUP_PATTERN);

  private PasswordPolicy() {}

  public static void validateSignup(String password) {
    if (password == null || password.length() > 128 || !SIGNUP.matcher(password).matches())
      throw BusinessException.bad(SIGNUP_MESSAGE);
  }

  public static void validateLogin(String password) {
    // Existing passwords are checked as entered, without changing or reapplying the signup policy.
    if (password == null || password.isBlank() || password.length() > 128)
      throw BusinessException.bad("비밀번호를 입력해주세요. 최대 128자입니다.");
  }
}
