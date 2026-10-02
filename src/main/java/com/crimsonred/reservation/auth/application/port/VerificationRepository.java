package com.crimsonred.reservation.auth.application.port;

import com.crimsonred.reservation.auth.domain.EmailVerification;
import java.util.Optional;

public interface VerificationRepository {
  Optional<EmailVerification> findByMemberId(Long id);

  Optional<EmailVerification> findByTokenHash(String hash);

  EmailVerification save(EmailVerification token);
}
