package com.crimsonred.reservation.auth.domain;

import static org.junit.jupiter.api.Assertions.*;

import com.crimsonred.reservation.member.domain.Member;
import com.crimsonred.reservation.booking.domain.Reservation;
import com.crimsonred.reservation.booking.domain.ReservationType;
import com.crimsonred.reservation.shared.domain.BusinessException;
import com.crimsonred.reservation.shared.domain.InputText;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class InputPolicyTest {
  @Test
  void hufsEmailRejectsMalformedOrUnsafeAddresses() {
    assertEquals("first.last+band@hufs.ac.kr", HufsEmail.normalize(" First.Last+band@HUFS.AC.KR "));
    assertDoesNotThrow(() -> HufsEmail.normalize("a".repeat(64) + "@hufs.ac.kr"));
    for (String email : new String[] {null, "", "a".repeat(65) + "@hufs.ac.kr", ".a@hufs.ac.kr",
        "a.@hufs.ac.kr", "a..b@hufs.ac.kr", "a b@hufs.ac.kr", "a@@hufs.ac.kr", "@hufs.ac.kr",
        "a@sub.hufs.ac.kr", "a@hufs.ac.kr.evil.com", "a@hufs.ac.kr\r\nBcc:x@evil.com",
        "a@hufs.ac.kr\n", "a\u200b@hufs.ac.kr", "한글@hufs.ac.kr", "\"a\"@hufs.ac.kr"})
      assertThrows(BusinessException.class, () -> HufsEmail.normalize(email));
  }

  @Test
  void passwordsRequireEnglishNumbersAndPunctuationWithoutWhitespace() {
    assertDoesNotThrow(() -> PasswordPolicy.validateSignup("Abcd123!"));
    assertDoesNotThrow(() -> PasswordPolicy.validateSignup("a1!" + "x".repeat(125)));
    for (String password : new String[] {null, "", " ".repeat(15), "Abc123!", "abcdefgh",
        "12345678", "Abcd1234", "1234567!", "Abcd 123!", "Abcd123!\n", "Abcd123!\u200b",
        "a1!" + "x".repeat(126)})
      assertThrows(BusinessException.class, () -> PasswordPolicy.validateSignup(password));
    assertDoesNotThrow(() -> PasswordPolicy.validateLogin("existing passphrase"));
    assertThrows(BusinessException.class, () -> PasswordPolicy.validateLogin(" ".repeat(15)));
  }

  @Test
  void namesAndReasonsEnforceLimitsBeforeChangingState() {
    assertEquals("홍길동", Member.register("a@hufs.ac.kr", " 홍길동 ", "hash", Instant.EPOCH).getName());
    assertDoesNotThrow(() -> Member.register("a@hufs.ac.kr", "가".repeat(10), "hash", Instant.EPOCH));
    for (String name : new String[] {null, "", " ", "\u00a0", "\u200b", "name\nother",
        "name\u0000other", "name\u2028other", "가".repeat(11)})
      assertThrows(BusinessException.class, () -> Member.register("a@hufs.ac.kr", name, "hash", Instant.EPOCH));
    assertDoesNotThrow(() -> reservation("가".repeat(20)));
    assertThrows(BusinessException.class, () -> reservation("가".repeat(21)));
    assertThrows(BusinessException.class, () -> reservation("\u200b"));
    assertEquals("첫 줄\n둘째 줄", InputText.multiline("첫 줄\n둘째 줄", 500, "사유"));
    assertDoesNotThrow(() -> InputText.multiline("가".repeat(500), 500, "사유"));
    assertThrows(BusinessException.class, () -> InputText.multiline("가".repeat(501), 500, "사유"));
    assertThrows(BusinessException.class, () -> InputText.multiline("사유\u0000", 500, "사유"));
    var approved = Member.register("a@hufs.ac.kr", "부원", "hash", Instant.EPOCH);
    approved.verifyEmail(Instant.EPOCH);
    approved.requestMembership();
    approved.reviewMembership(null, true, null, Instant.EPOCH);
    assertThrows(BusinessException.class, () -> approved.changeRole(null, 1));
    assertEquals("MEMBER", approved.getRole());
  }

  private Reservation reservation(String name) {
    return Reservation.create(1L, 1L, Instant.EPOCH, Instant.EPOCH.plusSeconds(1800), name,
        ReservationType.TEAM_REHEARSAL, "request", Instant.EPOCH);
  }
}
