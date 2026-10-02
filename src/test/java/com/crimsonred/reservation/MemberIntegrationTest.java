package com.crimsonred.reservation;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.crimsonred.reservation.shared.domain.BusinessException;
import com.crimsonred.reservation.shared.domain.BusinessException.Type;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class MemberIntegrationTest extends IntegrationTestSupport {
  @Test
  void adminsHaveOnlyMemberManagementEndpoints() throws Exception {
    var session = login(admin);
    assertEquals(200, request(session.client(), "GET", "/admin/members", null, null).statusCode());
    assertEquals(404, request(session.client(), "POST", "/admin/weeks", Map.of(), session.csrf()).statusCode());
    assertEquals(404, request(session.client(), "GET", "/admin/reservations", null, null).statusCode());
    assertEquals(404, request(session.client(), "GET", "/admin/announcements", null, null).statusCode());
    assertEquals(200, request(session.client(), "GET", "/weeks/booking/timetable", null, null).statusCode());
  }

  @Test
  void reviewWaitsForRoleChangeAndRejectsDemotedAdmin() throws Exception {
    var other = member("second", true, true);
    var applicant = member("applicant", false, false);
    tx.executeWithoutResult(status -> members.findById(applicant.getId()).orElseThrow().verifyEmail(NOW));
    memberService.request(applicant.getId());
    var locked = new CountDownLatch(1);
    var release = new CountDownLatch(1);
    try (var pool = Executors.newVirtualThreadPerTaskExecutor()) {
      var holder = pool.submit(() -> tx.executeWithoutResult(status -> {
        members.lockAdmins();
        members.locked(other.getId()).orElseThrow().changeRole("MEMBER", 2);
        locked.countDown();
        try {
          if (!release.await(5, TimeUnit.SECONDS)) throw new IllegalStateException("Lock timed out");
        } catch (InterruptedException e) {
          Thread.currentThread().interrupt();
          throw new IllegalStateException(e);
        }
      }));
      assertTrue(locked.await(5, TimeUnit.SECONDS));
      var waiting = pool.submit(() -> memberService.review(other.getId(), applicant.getId(), true, null));
      try {
        assertThrows(TimeoutException.class, () -> waiting.get(150, TimeUnit.MILLISECONDS));
      } finally {
        release.countDown();
      }
      holder.get(5, TimeUnit.SECONDS);
      var error = assertThrows(ExecutionException.class, () -> waiting.get(5, TimeUnit.SECONDS));
      assertEquals(Type.FORBIDDEN, ((BusinessException) error.getCause()).type);
      assertEquals("PENDING", members.findById(applicant.getId()).orElseThrow().getMembership());
    }
  }

  @Test
  void unapprovedAdminRoleDoesNotAllowRemovingTheLastActiveAdmin() {
    var unverified = member("unverified", false, false);
    jdbc.update("update member set role='ADMIN' where id=?", unverified.getId());
    assertEquals("LAST_ADMIN", assertThrows(BusinessException.class,
        () -> memberService.changeRole(admin.getId(), admin.getId(), "MEMBER")).code);
    assertEquals("LAST_ADMIN", assertThrows(BusinessException.class,
        () -> memberService.expel(admin.getId(), admin.getId(), "탈퇴")).code);
    assertTrue(members.findById(admin.getId()).orElseThrow().admin());
  }

  @Test
  void rejectionResubmissionAndLastAdminProtection() {
    var applicant = member("applicant", false, false);
    assertThrows(BusinessException.class, () -> memberService.request(applicant.getId()));
    tx.executeWithoutResult(
        s -> {
          var m = members.findById(applicant.getId()).orElseThrow();
          m.verifyEmail(NOW);
        });
    memberService.request(applicant.getId());
    memberService.review(admin.getId(), applicant.getId(), false, "명단 확인 필요");
    assertEquals(
        "명단 확인 필요", members.findById(applicant.getId()).orElseThrow().getRejectionReason());
    memberService.request(applicant.getId());
    memberService.review(admin.getId(), applicant.getId(), true, null);
    assertThrows(
        BusinessException.class,
        () -> memberService.changeRole(admin.getId(), admin.getId(), "MEMBER"));
    memberService.changeRole(admin.getId(), applicant.getId(), "ADMIN");
    memberService.changeRole(applicant.getId(), admin.getId(), "MEMBER");
    assertThrows(BusinessException.class, () -> memberService.admin(admin.getId()));
  }

  @Test
  void firstAdminIsAssignedInDatabaseAndVerificationDoesNotGrantPrivileges() throws Exception {
    tx.executeWithoutResult(
        status -> {
          var previous = members.findById(admin.getId()).orElseThrow();
          previous.changeRole("MEMBER", 2);
        });
    var owner = auth.signup("OWNER@HUFS.AC.KR", "최초 운영진", PASSWORD);
    var beforeVerification = members.findByEmail(owner.email()).orElseThrow();
    assertFalse(beforeVerification.admin());
    var browser = browser();
    assertEquals(
        200,
        request(
                browser,
                "POST",
                "/auth/verification/confirm",
                Map.of("token", owner.token()),
                csrf(browser))
            .statusCode());

    var verified = members.findByEmail(owner.email()).orElseThrow();
    assertNotNull(verified.getEmailVerifiedAt());
    assertFalse(verified.admin());
    assertEquals("MEMBER", verified.getRole());
    assertEquals("NOT_REQUESTED", verified.getMembership());
    assertEquals(0, jdbc.queryForObject("select count(*) from member where role='ADMIN'", Integer.class));
    assertThrows(BusinessException.class, () -> auth.verify(owner.token()));
    var session = login(verified);
    assertEquals(403, request(session.client(), "GET", "/admin/members", null, null).statusCode());
    jdbc.update("update member set role='ADMIN' where email=?", owner.email());
    assertEquals(403, request(session.client(), "GET", "/admin/members", null, null).statusCode());
    assertEquals(1, jdbc.update(
        "update member set role='ADMIN',membership='APPROVED',rejection_reason=null,"
            + "reviewed_at=UTC_TIMESTAMP(6) where email=? and email_verified_at is not null",
        owner.email()));
    var appointed = members.findByEmail(owner.email()).orElseThrow();
    assertTrue(appointed.admin());
    assertEquals(200, request(session.client(), "GET", "/admin/members", null, null).statusCode());
  }

  @Test
  void concurrentVerificationAndRoleDemotion() throws Exception {
    var payload = auth.signup("verify-" + UUID.randomUUID() + "@hufs.ac.kr", "신입", PASSWORD);
    try (var pool = Executors.newFixedThreadPool(2)) {
      var successful = new AtomicInteger();
      var a =
          pool.submit(
              () -> {
                try {
                  auth.verify(payload.token());
                  successful.incrementAndGet();
                } catch (BusinessException ignored) {
                }
              });
      var b =
          pool.submit(
              () -> {
                try {
                  auth.verify(payload.token());
                  successful.incrementAndGet();
                } catch (BusinessException ignored) {
                }
              });
      a.get(10, TimeUnit.SECONDS);
      b.get(10, TimeUnit.SECONDS);
      assertEquals(1, successful.get());
    }
    var other = member("other", true, true);
    try (var pool = Executors.newFixedThreadPool(2)) {
      var a =
          pool.submit(
              () -> {
                try {
                  memberService.changeRole(admin.getId(), admin.getId(), "MEMBER");
                } catch (BusinessException ignored) {
                }
              });
      var b =
          pool.submit(
              () -> {
                try {
                  memberService.changeRole(other.getId(), other.getId(), "MEMBER");
                } catch (BusinessException ignored) {
                }
              });
      a.get(10, TimeUnit.SECONDS);
      b.get(10, TimeUnit.SECONDS);
      assertEquals(
          1, jdbc.queryForObject("select count(*) from member where role='ADMIN'", Integer.class));
    }
  }

  @Test
  void roleRevocationAffectsExistingSession() throws Exception {
    var other = member("second", true, true);
    var session = login(other);
    assertEquals(200, request(session.client(), "GET", "/admin/members", null, null).statusCode());
    memberService.changeRole(admin.getId(), other.getId(), "MEMBER");
    assertEquals(403, request(session.client(), "GET", "/admin/members", null, null).statusCode());
  }

  @Test
  void expulsionRevokesExistingSessionPrivilegesAndPreservesRecords() throws Exception {
    var session = login(user);
    var operator = login(admin);
    var reservation = reserve(user, "before-expulsion", 540, 570);
    assertEquals(403, request(session.client(), "POST", "/admin/members/" + admin.getId() + "/expel",
        Map.of("reason", "권한 없음"), session.csrf()).statusCode());
    for (String reason : new String[] {"", " ", "\u200b", "사유\u0000", "가".repeat(501)})
      assertEquals(400, request(operator.client(), "POST", "/admin/members/" + user.getId() + "/expel",
          Map.of("reason", reason), operator.csrf()).statusCode());
    assertTrue(members.findById(user.getId()).orElseThrow().approved());
    var expelled = request(operator.client(), "POST", "/admin/members/" + user.getId() + "/expel",
        Map.of("reason", "가".repeat(500)), operator.csrf());
    assertEquals(200, expelled.statusCode(), expelled.body());
    assertEquals("EXPELLED", json.readTree(expelled.body()).path("membership").asString());
    assertEquals("MEMBER", json.readTree(expelled.body()).path("role").asString());
    assertEquals(409, request(session.client(), "POST", "/members/me/membership-request", null, session.csrf()).statusCode());
    assertEquals(403, request(session.client(), "POST", "/reservations",
        Map.of("weekStart", WEEK.toString(), "startAt", at(600).toString(), "endAt", at(630).toString(),
            "rehearsalName", "합주", "reservationType", "TEAM_REHEARSAL"), session.csrf()).statusCode());
    assertEquals(reservation.id(), bookings.get(user.getId(), reservation.id()).id());
    assertEquals(409, request(operator.client(), "POST", "/admin/members/" + admin.getId() + "/expel",
        Map.of("reason", "탈퇴"), operator.csrf()).statusCode());
    var other = member("other", true, true);
    var otherSession = login(other);
    assertEquals(200, request(operator.client(), "POST", "/admin/members/" + other.getId() + "/expel",
        Map.of("reason", "탈퇴"), operator.csrf()).statusCode());
    assertEquals(403, request(otherSession.client(), "GET", "/admin/members", null, null).statusCode());
    assertEquals("MEMBER", members.findById(other.getId()).orElseThrow().getRole());
    assertEquals(2, memberService.list(admin.getId(), "EXPELLED", 0, 20).totalElements());
    var visibleMembers = memberService.list(admin.getId(), null, 0, 1);
    assertEquals(1, visibleMembers.totalElements());
    assertEquals(admin.getId(), visibleMembers.content().getFirst().id());
    assertTrue(memberService.list(admin.getId(), null, 1, 1).content().isEmpty());
    var listed = request(operator.client(), "GET", "/admin/members?size=1", null, null);
    assertEquals(200, listed.statusCode(), listed.body());
    var page = json.readTree(listed.body());
    assertEquals(1, page.path("totalElements").asLong());
    assertEquals(admin.getId().longValue(), page.path("content").get(0).path("id").asLong());
    assertEquals(3, count("member"));
  }

  @Test
  void concurrentExpulsionAndRoleRevocationKeepOneAdmin() throws Exception {
    var other = member("other", true, true);
    var go = new CountDownLatch(1);
    try (var pool = Executors.newVirtualThreadPerTaskExecutor()) {
      var expulsion = pool.submit(() -> {
        go.await();
        try { memberService.expel(admin.getId(), admin.getId(), "탈퇴"); return true; }
        catch (BusinessException e) { assertEquals("LAST_ADMIN", e.code); return false; }
      });
      var demotion = pool.submit(() -> {
        go.await();
        try { memberService.changeRole(other.getId(), other.getId(), "MEMBER"); return true; }
        catch (BusinessException e) { assertEquals("LAST_ADMIN", e.code); return false; }
      });
      go.countDown();
      assertNotEquals(expulsion.get(15, TimeUnit.SECONDS), demotion.get(15, TimeUnit.SECONDS));
    }
    assertEquals(1, jdbc.queryForObject("select count(*) from member where role='ADMIN' and membership='APPROVED'", Integer.class));
  }
}
