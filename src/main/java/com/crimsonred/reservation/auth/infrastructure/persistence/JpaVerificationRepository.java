package com.crimsonred.reservation.auth.infrastructure.persistence;

import com.crimsonred.reservation.auth.application.port.VerificationRepository;
import com.crimsonred.reservation.auth.domain.EmailVerification;
import jakarta.persistence.EntityManager;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public class JpaVerificationRepository implements VerificationRepository {
  private final JpaVerificationQueries queries;
  private final EntityManager em;

  public JpaVerificationRepository(JpaVerificationQueries queries, EntityManager em) {
    this.queries = queries;
    this.em = em;
  }

  public Optional<EmailVerification> findByMemberId(Long id) {
    var result = queries.findByMemberId(id);
    result.ifPresent(em::refresh);
    return result;
  }

  public Optional<EmailVerification> findByTokenHash(String hash) {
    return queries.findByTokenHash(hash);
  }

  public EmailVerification save(EmailVerification token) {
    return queries.save(token);
  }
}

interface JpaVerificationQueries extends JpaRepository<EmailVerification, Long> {
  Optional<EmailVerification> findByMemberId(Long id);

  Optional<EmailVerification> findByTokenHash(String hash);
}
