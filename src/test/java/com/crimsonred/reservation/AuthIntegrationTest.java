package com.crimsonred.reservation;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.crimsonred.reservation.auth.application.AuthService;
import com.crimsonred.reservation.shared.domain.BusinessException;
import com.crimsonred.reservation.shared.domain.BusinessException.Type;
import java.net.CookieManager;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

class AuthIntegrationTest extends IntegrationTestSupport {
  @Test
  void malformedHttpRequestsKeepClientErrorStatus() throws Exception {
    var session = login(user);
    var wrongMethod = request(session.client(), "PUT", "/members/me", null, session.csrf());
    assertEquals(405, wrongMethod.statusCode(), wrongMethod.body());
    assertTrue(wrongMethod.headers().firstValue("Allow").orElse("").contains("GET"));
    var unsupportedBody = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/api/v1/auth/login"))
        .header("Content-Type", "text/plain").header("X-CSRF-TOKEN", session.csrf())
        .POST(HttpRequest.BodyPublishers.ofString("invalid")).build();
    var response = session.client().send(unsupportedBody, HttpResponse.BodyHandlers.ofString());
    assertEquals(415, response.statusCode(), response.body());
    var extremeDate = request(session.client(), "GET", "/weeks?from=%2B999999999-12-31&to=%2B999999999-12-31", null, null);
    assertEquals(400, extremeDate.statusCode(), extremeDate.body());
  }

  @Test
  void stalledDatabaseReturns503WithinSocketTimeoutAndRecovers() throws Exception {
    assertEquals("5000", ((com.zaxxer.hikari.HikariDataSource) dataSource)
        .getDataSourceProperties().getProperty("socketTimeout"));
    var session = login(user);
    MYSQL.getDockerClient().pauseContainerCmd(MYSQL.getContainerId()).exec();
    try {
      var httpRequest = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/api/v1/members/me"))
          .timeout(Duration.ofSeconds(20)).GET().build();
      var response = session.client().send(httpRequest, HttpResponse.BodyHandlers.ofString());
      assertEquals(503, response.statusCode(), response.body());
      assertFalse(response.body().contains("jdbc:mysql"));
    } finally {
      MYSQL.getDockerClient().unpauseContainerCmd(MYSQL.getContainerId()).exec();
    }
    assertEquals(200, request(session.client(), "GET", "/members/me", null, null).statusCode());
  }

  @Test
  void concurrentSignupsKeepOneAccountAndOneVerificationToken() throws Exception {
    String email = "same-signup@hufs.ac.kr";
    var go = new CountDownLatch(1);
    try (var pool = Executors.newVirtualThreadPerTaskExecutor()) {
      var calls = new ArrayList<Future<AuthService.MailPayload>>();
      for (int i = 0; i < 8; i++)
        calls.add(
            pool.submit(
                () -> {
                  go.await();
                  return auth.signup(email, "신규 부원", PASSWORD);
                }));
      go.countDown();
      int issued = 0;
      for (var call : calls) if (call.get(30, TimeUnit.SECONDS) != null) issued++;
      assertEquals(1, issued);
    }
    assertEquals(
        1, jdbc.queryForObject("select count(*) from member where email=?", Integer.class, email));
    assertEquals(
        1,
        jdbc.queryForObject(
            "select count(*) from email_verification v join member m on m.id=v.member_id where"
                + " m.email=?",
            Integer.class,
            email));
  }

  @Test
  void signupEmailCanBeVerifiedFromAnotherBrowserWithoutSharingSessions() throws Exception {
    var signupBrowser = browser();
    String email = "separate-browser@hufs.ac.kr";
    var signup = request(signupBrowser, "POST", "/auth/signup",
        Map.of("email", email, "name", "신입", "password", PASSWORD), csrf(signupBrowser));
    assertEquals(200, signup.statusCode(), signup.body());
    var sent = org.mockito.ArgumentCaptor.forClass(jakarta.mail.internet.MimeMessage.class);
    verify(mail).send(sent.capture());
    String token = verificationToken(sent.getValue());
    var verificationBrowser = browser();
    var confirmation = request(verificationBrowser, "POST", "/auth/verification/confirm",
        Map.of("token", token), csrf(verificationBrowser));
    assertEquals(200, confirmation.statusCode(), confirmation.body());
    assertEquals("이메일 인증을 완료했습니다.", json.readTree(confirmation.body()).path("message").asString());
    assertNotNull(members.findByEmail(email).orElseThrow().getEmailVerifiedAt());
    assertEquals(401, request(verificationBrowser, "GET", "/members/me", null, null).statusCode());
    var replay = request(signupBrowser, "POST", "/auth/verification/confirm",
        Map.of("token", token), csrf(signupBrowser));
    assertEquals(400, replay.statusCode(), replay.body());
    var other = auth.signup("logged-browser@hufs.ac.kr", "신입", PASSWORD);
    var existingSession = login(user);
    var otherConfirmation = request(existingSession.client(), "POST", "/auth/verification/confirm",
        Map.of("token", other.token()), existingSession.csrf());
    assertEquals(200, otherConfirmation.statusCode(), otherConfirmation.body());
    assertNotNull(members.findByEmail(other.email()).orElseThrow().getEmailVerifiedAt());
    assertEquals(user.getId().longValue(), json.readTree(
        request(existingSession.client(), "GET", "/members/me", null, null).body()).path("id").asLong());
  }

  @Test
  void resendReturnsJsonAndNewLinkWorksInAnotherBrowser() throws Exception {
    var original = auth.signup("resend-browser@hufs.ac.kr", "신입", PASSWORD);
    when(clock.instant()).thenReturn(NOW.plusSeconds(60));
    var resendBrowser = browser();
    String csrf = csrf(resendBrowser);
    var response = request(resendBrowser, "POST", "/auth/verification/resend",
        Map.of("email", original.email()), csrf);
    assertEquals(200, response.statusCode(), response.body());
    assertEquals("인증이 필요한 이메일이면 메일을 발송했습니다.",
        json.readTree(response.body()).path("message").asString());
    var sent = org.mockito.ArgumentCaptor.forClass(jakarta.mail.internet.MimeMessage.class);
    verify(mail).send(sent.capture());
    String newToken = verificationToken(sent.getValue());
    var verificationBrowser = browser();
    String verificationCsrf = csrf(verificationBrowser);
    var stale = request(verificationBrowser, "POST", "/auth/verification/confirm",
        Map.of("token", original.token()), verificationCsrf);
    assertEquals(400, stale.statusCode(), stale.body());
    var confirmation = request(verificationBrowser, "POST", "/auth/verification/confirm",
        Map.of("token", newToken), verificationCsrf);
    assertEquals(200, confirmation.statusCode(), confirmation.body());
    assertNotNull(members.findByEmail(original.email()).orElseThrow().getEmailVerifiedAt());
    var alreadyVerified = request(resendBrowser, "POST", "/auth/verification/resend",
        Map.of("email", original.email()), csrf);
    assertEquals(200, alreadyVerified.statusCode(), alreadyVerified.body());
    verify(mail, times(1)).send(any(jakarta.mail.internet.MimeMessage.class));
  }

  @Test
  void emailTokenSingleUseAndExpiry() {
    for (String token : new String[] {null, "", "x".repeat(44), "!".repeat(43)})
      assertEquals(Type.INVALID_INPUT, assertThrows(BusinessException.class, () -> auth.verify(token)).type);
    var payload = auth.signup("new-" + UUID.randomUUID() + "@hufs.ac.kr", "신입", PASSWORD);
    auth.verify(payload.token());
    assertThrows(BusinessException.class, () -> auth.verify(payload.token()));
    var other = auth.signup("expired-" + UUID.randomUUID() + "@hufs.ac.kr", "신입", PASSWORD);
    when(clock.instant()).thenReturn(NOW.plusSeconds(1800));
    assertThrows(BusinessException.class, () -> auth.verify(other.token()));
  }

  @Test
  void failedEmailDeliveryCanBeReissuedAndOldTokenIsInvalidated() {
    var payload = auth.signup("mail-" + UUID.randomUUID() + "@hufs.ac.kr", "신입", PASSWORD);
    doThrow(new org.springframework.mail.MailSendException("SMTP unavailable"))
        .when(mail)
        .send(any(jakarta.mail.internet.MimeMessage.class));
    assertDoesNotThrow(() -> verificationMail.send(payload));
    assertNull(auth.resend(payload.email()));
    when(clock.instant()).thenReturn(NOW.plusSeconds(60));
    var next = auth.resend(payload.email());
    assertNotNull(next);
    assertThrows(BusinessException.class, () -> auth.verify(payload.token()));
    auth.verify(next.token());
  }

  @Test
  void realSessionCsrfAuthorizationAndLogout() throws Exception {
    var anonymous = browser();
    assertEquals(401, request(anonymous, "GET", "/members/me", null, null).statusCode());
    assertEquals(
        403,
        request(
                anonymous,
                "POST",
                "/auth/login",
                Map.of("email", user.getEmail(), "password", PASSWORD),
                null)
            .statusCode());
    var u = login(user);
    assertEquals(200, request(u.client(), "GET", "/members/me", null, null).statusCode());
    assertEquals(403, request(u.client(), "GET", "/admin/members", null, null).statusCode());
    assertEquals(404, request(u.client(), "GET", "/reservations/999999", null, null).statusCode());
    var pending = member("unapproved", false, false);
    var p = login(pending);
    assertEquals(
        403,
        request(
                p.client(),
                "POST",
                "/reservations",
                Map.of(
                    "weekStart",
                    WEEK.toString(),
                    "startAt",
                    at(540).toString(),
                    "endAt",
                    at(600).toString(),
                    "rehearsalName",
                    "합주",
                    "reservationType",
                    "TEAM_REHEARSAL"),
                p.csrf())
            .statusCode());
    assertEquals(200, request(u.client(), "POST", "/auth/logout", null, u.csrf()).statusCode());
    assertEquals(401, request(u.client(), "GET", "/members/me", null, null).statusCode());
  }

  @Test
  void inputValidationRejectsWeakPasswordsWrongJsonTypesAndMissingDates() throws Exception {
    var client = browser();
    var token = csrf(client);
    for (Object password : new Object[] {" ".repeat(15), "Abc123!", "abcdefgh", "Abcd1234",
        "1234567!", "Abcd 123!", 123456789012345L, true, 12.5}) {
      var response = request(client, "POST", "/auth/signup",
          Map.of("email", "new@hufs.ac.kr", "name", "신입", "password", password), token);
      assertEquals(400, response.statusCode(), response.body());
    }
    for (Object name : new Object[] {"가".repeat(11), "\u200b", "\u00a0", "홍\u0000길동", 123, true}) {
      var response = request(client, "POST", "/auth/signup",
          Map.of("email", "new@hufs.ac.kr", "name", name, "password", "Abcd123!"), token);
      assertEquals(400, response.statusCode(), response.body());
    }
    assertEquals(400, request(client, "POST", "/auth/signup", Map.of(), token).statusCode());
    assertEquals(400, request(client, "POST", "/auth/signup",
        Map.of("email", "new.@hufs.ac.kr", "name", "신입", "password", "Abcd123!"), token).statusCode());
    assertTrue(members.findByEmail("new@hufs.ac.kr").isEmpty());
    assertEquals(200, request(client, "POST", "/auth/signup",
        Map.of("email", "new@hufs.ac.kr", "name", "가".repeat(10), "password", "Abcd123!"), token).statusCode());
    assertEquals(10, members.findByEmail("new@hufs.ac.kr").orElseThrow().getName().length());
    assertNotNull(auth.login("new@hufs.ac.kr", "Abcd123!"));
    assertEquals(400, request(client, "POST", "/auth/login",
        Map.of("email", user.getEmail(), "password", 123456789012345L), token).statusCode());

    var session = login(user);
    for (String path : new String[] {"/weeks", "/weeks?from=2027-01-04", "/weeks?to=2027-01-11"}) {
      var response = request(session.client(), "GET", path, null, null);
      assertEquals(400, response.statusCode(), response.body());
      assertFalse(json.readTree(response.body()).path("fieldErrors").isEmpty());
    }
  }

  @Test
  void expiredRedisSessionCannotAuthenticate() throws Exception {
    var browser = login(user);
    var manager = (CookieManager) browser.client().cookieHandler().orElseThrow();
    var cookie =
        manager.getCookieStore().getCookies().stream()
            .filter(c -> c.getName().equals("CRIMSON_SESSION"))
            .findFirst()
            .orElseThrow();
    var id =
        new String(
            Base64.getDecoder().decode(cookie.getValue()), java.nio.charset.StandardCharsets.UTF_8);
    var session = sessions.findById(id);
    assertNotNull(session);
    assertEquals(Duration.ofHours(2), session.getMaxInactiveInterval());
    session.setLastAccessedTime(Instant.now().minusSeconds(7201));
    sessions.save(session);
    assertEquals(401, request(browser.client(), "GET", "/members/me", null, null).statusCode());
  }

  @Test
  void rateLimiterIsAtomicAndRedisFailurePreservesBookings() throws Exception {
    var limitedEmail = "rate-" + UUID.randomUUID() + "@hufs.ac.kr";
    for (int i = 0; i < 10; i++) limiter.login("test-ip", limitedEmail);
    assertEquals(
        Type.RATE_LIMITED,
        assertThrows(BusinessException.class, () -> limiter.login("test-ip", limitedEmail)).type);
    reserve(user, "durable", 540, 600);
    var session = login(user);
    REDIS.getDockerClient().pauseContainerCmd(REDIS.getContainerId()).exec();
    try {
      assertEquals(503, request(session.client(), "GET", "/members/me", null, null).statusCode());
      assertEquals(1, count("reservation"));
      assertEquals(2, count("active_slot_claim"));
    } finally {
      REDIS.getDockerClient().unpauseContainerCmd(REDIS.getContainerId()).exec();
    }
  }

  private String verificationToken(jakarta.mail.internet.MimeMessage message) throws Exception {
    var mixed = (jakarta.mail.Multipart) message.getContent();
    var related = (jakarta.mail.Multipart) mixed.getBodyPart(0).getContent();
    var alternative = (jakarta.mail.Multipart) related.getBodyPart(0).getContent();
    var link = java.util.regex.Pattern.compile("/verify\\?token=([A-Za-z0-9_-]{43})")
        .matcher((String) alternative.getBodyPart(0).getContent());
    assertTrue(link.find());
    return link.group(1);
  }
}
