package com.crimsonred.reservation.auth.domain;

import com.crimsonred.reservation.shared.domain.BusinessException;
import java.util.Locale;
import java.util.regex.Pattern;

/** HUFS addresses use an ASCII dot-atom local part; mailbox ownership requires email verification. */
public final class HufsEmail {
  private static final String ATOM = "[a-z0-9!#$%&'*+/=?^_`{|}~-]+";
  private static final Pattern ADDRESS = Pattern.compile(ATOM + "(?:\\." + ATOM + ")*@hufs\\.ac\\.kr");

  private HufsEmail() {}

  public static String normalize(String email) {
    if (email == null || email.length() > 254 || email.codePoints().anyMatch(Character::isISOControl))
      throw BusinessException.bad("올바른 HUFS 이메일(@hufs.ac.kr)을 입력해주세요.");
    String value = email.strip().toLowerCase(Locale.ROOT);
    int localLength = value.indexOf('@');
    if (localLength < 1 || localLength > 64 || !ADDRESS.matcher(value).matches())
      throw BusinessException.bad("올바른 HUFS 이메일(@hufs.ac.kr)을 입력해주세요.");
    return value;
  }
}
