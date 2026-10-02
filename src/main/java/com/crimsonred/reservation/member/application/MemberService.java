package com.crimsonred.reservation.member.application;

import com.crimsonred.reservation.member.application.port.MemberRepository;
import com.crimsonred.reservation.member.domain.Member;
import com.crimsonred.reservation.shared.application.PageQuery;
import com.crimsonred.reservation.shared.application.PageResult;
import com.crimsonred.reservation.shared.application.port.TransactionRunner;
import com.crimsonred.reservation.shared.domain.BusinessException;
import com.crimsonred.reservation.shared.domain.BusinessException.Type;
import java.time.Clock;
import java.util.Set;

public class MemberService {
  private final MemberRepository members;
  private final Clock clock;
  private final TransactionRunner transactions;

  public MemberService(MemberRepository members, Clock clock, TransactionRunner transactions) {
    this.members = members;
    this.clock = clock;
    this.transactions = transactions;
  }

  public record View(
      Long id,
      String email,
      String name,
      boolean emailVerified,
      String membership,
      String role,
      String rejectionReason) {}

  public View view(Member m) {
    return new View(
        m.getId(),
        m.getEmail(),
        m.getName(),
        m.getEmailVerifiedAt() != null,
        m.getMembership(),
        m.getRole(),
        m.getRejectionReason());
  }

  public Member current(Long id) {
    if (id == null)
      throw new BusinessException(Type.UNAUTHENTICATED, "UNAUTHENTICATED", "로그인이 필요합니다.");
    return members
        .findById(id)
        .orElseThrow(
            () -> new BusinessException(Type.UNAUTHENTICATED, "UNAUTHENTICATED", "다시 로그인해주세요."));
  }

  public Member approved(Long id) {
    return requireApproved(current(id));
  }

  /** Called inside the booking transaction, after acquiring the week lock. */
  public Member approvedForUpdate(Long id) {
    return requireApproved(members.locked(id).orElseThrow(BusinessException::missing));
  }

  private Member requireApproved(Member m) {
    if (!m.approved())
      throw new BusinessException(Type.FORBIDDEN, "MEMBERSHIP_REQUIRED", "이메일 인증과 부원 승인이 필요합니다.");
    return m;
  }

  public Member admin(Long id) {
    var m = current(id);
    if (!m.admin()) throw new BusinessException(Type.FORBIDDEN, "ADMIN_REQUIRED", "운영진 권한이 필요합니다.");
    return m;
  }

  public View request(Long id) {
    return transactions.required(
        () -> {
          var m = members.locked(id).orElseThrow(BusinessException::missing);
          m.requestMembership();
          members.save(m);
          return view(m);
        });
  }

  public View review(Long actor, Long id, boolean approve, String reason) {
    return transactions.required(
        () -> {
          // Use the same lock order as role changes and expulsion before checking authority.
          members.lockAdmins();
          admin(actor);
          var m = members.locked(id).orElseThrow(BusinessException::missing);
          m.reviewMembership(actor, approve, reason, clock.instant());
          members.save(m);
          return view(m);
        });
  }

  public View changeRole(Long actor, Long id, String role) {
    return transactions.required(
        () -> {
          // Serialize role changes, including concurrent attempts to demote the last two admins.
          var admins = members.lockAdmins();
          admin(actor);
          var m = members.locked(id).orElseThrow(BusinessException::missing);
          m.changeRole(role, admins.size());
          members.save(m);
          return view(m);
        });
  }

  public View expel(Long actor, Long id, String reason) {
    return transactions.required(
        () -> {
          var admins = members.lockAdmins();
          admin(actor);
          var m = members.locked(id).orElseThrow(BusinessException::missing);
          m.expel(actor, reason, clock.instant(), admins.size());
          members.save(m);
          return view(m);
        });
  }

  public PageResult<View> list(Long actor, String membership, int page, int size) {
    admin(actor);
    if (membership != null && !Set.of("NOT_REQUESTED", "PENDING", "APPROVED", "REJECTED", "EXPELLED").contains(membership))
      throw BusinessException.bad("잘못된 부원 상태입니다.");
    var pageable = new PageQuery(page, size, "id");
    return (membership == null
            ? members.findByMembershipNot("EXPELLED", pageable)
            : members.findByMembership(membership, pageable))
        .map(this::view);
  }
}
