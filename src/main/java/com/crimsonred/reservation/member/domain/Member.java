package com.crimsonred.reservation.member.domain;

import com.crimsonred.reservation.shared.domain.BusinessException;
import com.crimsonred.reservation.shared.domain.BusinessException.Type;
import com.crimsonred.reservation.shared.domain.InputText;
import java.time.Instant;

public class Member {
  public static final int NAME_MAX_LENGTH = 10;
  public static final int REASON_MAX_LENGTH = 500;
  private Long id;
  private String email;
  private String name;
  private String passwordHash;
  private Instant emailVerifiedAt;
  private String membership = "NOT_REQUESTED";
  private String role = "MEMBER";
  private String rejectionReason;
  private Long reviewedBy;
  private Instant reviewedAt;
  private Instant createdAt;

  public boolean approved() {
    return emailVerifiedAt != null && membership.equals("APPROVED");
  }

  public boolean admin() {
    return approved() && role.equals("ADMIN");
  }

  public Long getId() {
    return id;
  }

  public String getEmail() {
    return email;
  }

  public String getName() {
    return name;
  }

  public String getPasswordHash() {
    return passwordHash;
  }

  public Instant getEmailVerifiedAt() {
    return emailVerifiedAt;
  }

  public String getMembership() {
    return membership;
  }

  public String getRole() {
    return role;
  }

  public String getRejectionReason() {
    return rejectionReason;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  protected Member() {}

  public static Member register(String email, String name, String passwordHash, Instant createdAt) {
    var member = new Member();
    member.email = email;
    member.name = InputText.singleLine(name, NAME_MAX_LENGTH, "이름");
    member.passwordHash = passwordHash;
    member.createdAt = createdAt;
    return member;
  }

  public void verifyEmail(Instant verifiedAt) {
    emailVerifiedAt = verifiedAt;
  }

  public void requestMembership() {
    if (emailVerifiedAt == null)
      throw new BusinessException(Type.FORBIDDEN, "EMAIL_REQUIRED", "이메일 인증을 완료해주세요.");
    if (!membership.equals("NOT_REQUESTED") && !membership.equals("REJECTED"))
      throw BusinessException.conflict("MEMBERSHIP_STATE", "이미 신청했거나 승인된 회원입니다.");
    membership = "PENDING";
    rejectionReason = null;
  }

  public void reviewMembership(Long actor, boolean approve, String reason, Instant now) {
    if (!membership.equals("PENDING"))
      throw BusinessException.conflict("MEMBERSHIP_STATE", "승인 대기 회원이 아닙니다.");
    String validatedReason = approve ? null : InputText.multiline(reason, REASON_MAX_LENGTH, "거절 사유");
    membership = approve ? "APPROVED" : "REJECTED";
    rejectionReason = validatedReason;
    reviewedBy = actor;
    reviewedAt = now;
  }

  public void expel(Long actor, String reason, Instant now, int adminCount) {
    if (!membership.equals("APPROVED"))
      throw BusinessException.conflict("MEMBERSHIP_STATE", "승인된 부원만 자격을 해제할 수 있습니다.");
    if (role.equals("ADMIN") && adminCount <= 1)
      throw BusinessException.conflict("LAST_ADMIN", "마지막 운영진은 부원 자격을 해제할 수 없습니다.");
    String validatedReason = InputText.multiline(reason, REASON_MAX_LENGTH, "자격 해제 사유");
    membership = "EXPELLED";
    role = "MEMBER";
    rejectionReason = validatedReason;
    reviewedBy = actor;
    reviewedAt = now;
  }

  public void changeRole(String newRole, int adminCount) {
    if (!approved())
      throw BusinessException.conflict("MEMBERSHIP_REQUIRED", "승인된 부원에게만 운영진 권한을 부여할 수 있습니다.");
    if (!"ADMIN".equals(newRole) && !"MEMBER".equals(newRole))
      throw BusinessException.bad("잘못된 권한입니다.");
    if (role.equals("ADMIN") && newRole.equals("MEMBER") && adminCount <= 1)
      throw BusinessException.conflict("LAST_ADMIN", "마지막 운영진 권한은 회수할 수 없습니다.");
    role = newRole;
  }
}
