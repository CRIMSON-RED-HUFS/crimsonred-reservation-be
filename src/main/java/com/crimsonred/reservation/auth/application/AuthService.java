package com.crimsonred.reservation.auth.application;

import com.crimsonred.reservation.auth.application.port.PasswordHasher;
import com.crimsonred.reservation.auth.application.port.VerificationRepository;
import com.crimsonred.reservation.auth.domain.EmailVerification;
import com.crimsonred.reservation.auth.domain.HufsEmail;
import com.crimsonred.reservation.auth.domain.PasswordPolicy;
import com.crimsonred.reservation.member.application.port.MemberRepository;
import com.crimsonred.reservation.member.domain.Member;
import com.crimsonred.reservation.shared.application.StorageConflict;
import com.crimsonred.reservation.shared.application.port.TransactionRunner;
import com.crimsonred.reservation.shared.domain.BusinessException;
import com.crimsonred.reservation.shared.domain.BusinessException.Type;
import com.crimsonred.reservation.shared.domain.InputText;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.time.*;
import java.util.*;

public class AuthService {
  private final MemberRepository members;
  private final VerificationRepository tokens;
  private final PasswordHasher passwords;
  private final Clock clock;
  private final TransactionRunner transactions;
  private final String dummyHash;

  public AuthService(
      MemberRepository members,
      VerificationRepository tokens,
      PasswordHasher passwords,
      Clock clock,
      TransactionRunner transactions) {
    this.members = members;
    this.tokens = tokens;
    this.passwords = passwords;
    this.clock = clock;
    this.transactions = transactions;
    dummyHash = passwords.encode(UUID.randomUUID().toString());
  }

  public static String normalize(String email) {
    return HufsEmail.normalize(email);
  }

  public record MailPayload(String email, String name, String token) {}

  public MailPayload signup(String email, String name, String password) {
    String normalizedEmail = normalize(email);
    PasswordPolicy.validateSignup(password);
    String normalizedName = InputText.singleLine(
        name, Member.NAME_MAX_LENGTH, "이름");
    String hash = passwords.encode(password);
    try {
      return transactions.required(
          () -> {
            if (members.findByEmail(normalizedEmail).isPresent()) return null;
            var m = Member.register(normalizedEmail, normalizedName, hash, clock.instant());
            members.saveAndFlush(m);
            return issue(m);
          });
    } catch (StorageConflict conflict) {
      // A concurrent signup must not disclose whether the email already exists.
      if (members.findByEmail(normalizedEmail).isPresent()) return null;
      throw conflict;
    }
  }

  public Member login(String email, String password) {
    String normalizedEmail = normalize(email);
    PasswordPolicy.validateLogin(password);
    var m = members.findByEmail(normalizedEmail).orElse(null);
    boolean valid = passwords.matches(password, m == null ? dummyHash : m.getPasswordHash());
    if (!valid || m == null)
      throw new BusinessException(Type.UNAUTHENTICATED, "LOGIN_FAILED", "이메일 또는 비밀번호를 확인해주세요.");
    return m;
  }

  public MailPayload resend(String email) {
    return transactions.required(
        () -> {
          var found = members.findByEmail(normalize(email));
          if (found.isEmpty()) return null;
          var m = members.locked(found.get().getId()).orElseThrow(BusinessException::missing);
          if (m.getEmailVerifiedAt() != null) return null;
          var old = tokens.findByMemberId(m.getId());
          if (old.isPresent()
              && old.get()
                  .getExpiresAt()
                  .minusSeconds(1800)
                  .plusSeconds(60)
                  .isAfter(clock.instant())) return null;
          return issue(m);
        });
  }

  private MailPayload issue(Member m) {
    byte[] bytes = new byte[32];
    new SecureRandom().nextBytes(bytes);
    String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    var t = tokens.findByMemberId(m.getId()).orElseGet(EmailVerification::new);
    t.renew(m.getId(), digest(token), clock.instant().plusSeconds(1800));
    tokens.save(t);
    return new MailPayload(m.getEmail(), m.getName(), token);
  }

  public void verify(String token) {
    if (token == null || !token.matches("[A-Za-z0-9_-]{43}"))
      throw BusinessException.bad("유효하지 않은 인증 링크입니다.");
    String tokenHash = digest(token);
    transactions.required(
        () -> {
          var found =
              tokens
                  .findByTokenHash(tokenHash)
                  .orElseThrow(() -> BusinessException.bad("유효하지 않은 인증 링크입니다."));
          var m = members.locked(found.getMemberId()).orElseThrow(BusinessException::missing);
          // Re-read after locking the member, so resend and token consumption use the same lock.
          var t = tokens.findByMemberId(m.getId()).orElseThrow(BusinessException::missing);
          t.consume(tokenHash, clock.instant());
          m.verifyEmail(t.getUsedAt());
          tokens.save(t);
          members.save(m);
        });
  }

  public static String digest(String value) {
    try {
      return HexFormat.of()
          .formatHex(
              MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException(e);
    }
  }
}
