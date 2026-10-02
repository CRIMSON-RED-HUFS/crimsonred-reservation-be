package com.crimsonred.reservation.member.application.port;

import com.crimsonred.reservation.member.domain.Member;
import com.crimsonred.reservation.shared.application.PageQuery;
import com.crimsonred.reservation.shared.application.PageResult;
import java.util.List;
import java.util.Optional;

public interface MemberRepository {
  Optional<Member> findById(Long id);

  Optional<Member> findByEmail(String email);

  Optional<Member> locked(Long id);

  List<Member> lockAdmins();

  Member save(Member member);

  Member saveAndFlush(Member member);

  PageResult<Member> findByMembershipNot(String membership, PageQuery page);

  PageResult<Member> findByMembership(String membership, PageQuery page);
}
