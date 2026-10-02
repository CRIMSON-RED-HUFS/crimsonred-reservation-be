package com.crimsonred.reservation.auth.application.port;

import com.crimsonred.reservation.auth.application.AuthService;

public interface VerificationEmailSender {
  void send(AuthService.MailPayload payload);
}
