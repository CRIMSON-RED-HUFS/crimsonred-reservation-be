package com.crimsonred.reservation.shared.domain;

import java.util.regex.Pattern;

public final class InputText {
  public static final String SINGLE_LINE_PATTERN = "[^\\p{Cc}\\p{Cf}\\p{Cs}\\p{Zl}\\p{Zp}]+";
  public static final String MULTILINE_PATTERN = "(?:[\\t\\r\\n]|[^\\p{Cc}\\p{Cf}\\p{Cs}])+";
  private static final Pattern SINGLE_LINE = Pattern.compile(SINGLE_LINE_PATTERN);
  private static final Pattern MULTILINE = Pattern.compile(MULTILINE_PATTERN);

  private InputText() {}

  public static String singleLine(String value, int maxLength, String label) {
    return required(value, maxLength, label, SINGLE_LINE);
  }

  public static String multiline(String value, int maxLength, String label) {
    return required(value, maxLength, label, MULTILINE);
  }

  private static String required(String value, int maxLength, String label, Pattern allowed) {
    if (value == null || value.length() > maxLength || !allowed.matcher(value).matches()
        || value.codePoints().allMatch(c -> Character.isWhitespace(c) || Character.isSpaceChar(c)))
      throw BusinessException.bad(label + ": 공백이나 보이지 않는 문자만 입력할 수 없으며 최대 " + maxLength + "자입니다.");
    return value.strip();
  }
}
