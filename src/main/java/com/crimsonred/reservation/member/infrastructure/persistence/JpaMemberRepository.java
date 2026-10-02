package com.crimsonred.reservation.member.infrastructure.persistence;

import com.crimsonred.reservation.member.application.port.MemberRepository;
import com.crimsonred.reservation.member.domain.Member;
import com.crimsonred.reservation.shared.application.PageQuery;
import com.crimsonred.reservation.shared.application.PageResult;
import com.crimsonred.reservation.shared.infrastructure.SpringPages;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public class JpaMemberRepository implements MemberRepository {
  private final JpaMemberQueries queries;
  private final EntityManager em;

  public JpaMemberRepository(JpaMemberQueries queries, EntityManager em) {
    this.queries = queries;
    this.em = em;
  }

  public Optional<Member> findById(Long id) {
    return queries.findById(id);
  }

  public Optional<Member> findByEmail(String email) {
    return queries.findByEmail(email);
  }

  public Optional<Member> locked(Long id) {
    var result = queries.locked(id);
    result.ifPresent(em::refresh);
    return result;
  }

  public List<Member> lockAdmins() {
    return queries.lockAdmins();
  }

  public Member save(Member member) {
    return queries.save(member);
  }

  public Member saveAndFlush(Member member) {
    return queries.saveAndFlush(member);
  }

  public PageResult<Member> findByMembershipNot(String membership, PageQuery page) {
    return SpringPages.result(
        queries.findByMembershipNot(membership, SpringPages.request(page)), page);
  }

  public PageResult<Member> findByMembership(String membership, PageQuery page) {
    return SpringPages.result(
        queries.findByMembership(membership, SpringPages.request(page)), page);
  }
}

interface JpaMemberQueries extends JpaRepository<Member, Long> {
  Optional<Member> findByEmail(String email);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select m from Member m where m.id=:id")
  Optional<Member> locked(@Param("id") Long id);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select m from Member m where m.role='ADMIN' and m.membership='APPROVED'"
      + " and m.emailVerifiedAt is not null order by m.id")
  List<Member> lockAdmins();

  Page<Member> findByMembership(String membership, Pageable pageable);

  Page<Member> findByMembershipNot(String membership, Pageable pageable);
}
