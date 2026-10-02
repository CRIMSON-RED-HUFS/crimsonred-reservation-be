package com.crimsonred.reservation;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.crimsonred.reservation.booking.application.BookingService.BookingInput;
import com.crimsonred.reservation.booking.application.BookingService.ReservationResult;
import com.crimsonred.reservation.booking.application.BookingService.Timetable;
import com.crimsonred.reservation.booking.application.ReservationView;
import com.crimsonred.reservation.booking.domain.BookingRules;
import com.crimsonred.reservation.booking.domain.ReservationType;
import com.crimsonred.reservation.booking.domain.WeeklySchedule;
import com.crimsonred.reservation.member.domain.Member;
import com.crimsonred.reservation.shared.domain.BusinessException;
import com.crimsonred.reservation.shared.domain.BusinessException.Type;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class BookingIntegrationTest extends IntegrationTestSupport {
  @Test
  void sunday23RolloverKeepsReadsPureAndProvisionsOnceOnConcurrentBookings() throws Exception {
    var original = reserve(user, "previous-week", 540, 600);
    var monday = WEEK.plusWeeks(1);
    when(clock.instant()).thenReturn(WeeklySchedule.opensAt(monday).minusNanos(1));
    assertEquals(WEEK, bookings.bookingTimetable(user.getId()).weekStart());
    assertTrue(bookings.timetable(user.getId(), monday).slots().isEmpty());
    when(clock.instant()).thenReturn(WeeklySchedule.opensAt(monday));
    try (var pool = Executors.newFixedThreadPool(10)) {
      var tasks = new ArrayList<Future<Timetable>>();
      for (int i = 0; i < 10; i++)
        tasks.add(pool.submit(() -> bookings.bookingTimetable(user.getId())));
      for (var task : tasks) {
        var table = task.get(10, TimeUnit.SECONDS);
        assertEquals(monday, table.weekStart());
        assertEquals(189, table.slots().size());
      }
    }
    var session = login(user);
    for (String path : new String[] {"/weeks/booking/timetable", "/weeks/" + monday + "/timetable"}) {
      var response = request(session.client(), "GET", path, null, null);
      assertEquals(200, response.statusCode(), response.body());
      var table = json.readValue(response.body(), Timetable.class);
      assertEquals(189, table.slots().size());
      assertTrue(table.slots().stream().allMatch(s -> s.id() == null && "AVAILABLE".equals(s.status())));
    }
    var listed = request(session.client(), "GET", "/weeks?from=" + monday + "&to=" + monday, null, null);
    assertEquals(200, listed.statusCode(), listed.body());
    var listedWeek = json.readTree(listed.body());
    assertEquals(1, listedWeek.size());
    assertTrue(listedWeek.get(0).path("id").isNull());
    assertTrue(listedWeek.get(0).path("published").asBoolean());
    assertEquals(1, count("booking_week"));
    assertEquals(189, count("booking_slot"));
    assertEquals(2, count("active_slot_claim"));
    var start = monday.atStartOfDay(BookingRules.SEOUL).plusHours(9).toInstant();
    var input = new BookingInput(monday, start, start.plusSeconds(3600), "다음 주 합주",
        ReservationType.TEAM_REHEARSAL);
    try (var pool = Executors.newFixedThreadPool(10)) {
      var tasks = new ArrayList<Future<ReservationResult>>();
      for (int i = 0; i < 10; i++)
        tasks.add(pool.submit(() -> bookings.reserve(user.getId(), "first-use", input)));
      var ids = new HashSet<Long>();
      int created = 0;
      for (var task : tasks) {
        var result = task.get(10, TimeUnit.SECONDS);
        ids.add(result.reservation().id());
        if (result.created()) created++;
      }
      assertEquals(1, ids.size());
      assertEquals(1, created);
    }
    assertEquals(2, count("booking_week"));
    assertEquals(378, count("booking_slot"));
    assertEquals(4, count("active_slot_claim"));
    assertTrue(bookings.mine(user.getId(), 0, 20, "upcoming").content().stream()
        .anyMatch(r -> original.id().equals(r.id())));
    assertEquals("BOOKING_CLOSED",
        assertThrows(BusinessException.class, () -> reserve(user, "old-week", 600, 630)).code);
    when(clock.instant()).thenReturn(WeeklySchedule.opensAt(monday.plusWeeks(3)));
    assertEquals(monday.plusWeeks(3), bookings.bookingTimetable(user.getId()).weekStart());
    assertEquals(2, count("booking_week"));
    bookings.ensureBookingWeek(); // Commands catch up after downtime without duplicating old weeks.
    assertEquals(3, count("booking_week"));
    assertTrue(bookings.mine(user.getId(), 0, 20, "history").content().stream()
        .anyMatch(r -> original.id().equals(r.id())));
  }

  @Test
  void historicalTimetablesAreReadOnlyAndKeepDetailsWithoutCreatingMissingWeeks() throws Exception {
    var reservation = reserve(user, "historical-booking", 540, 600);
    var cancelled = reserve(user, "historical-cancelled", 600, 630);
    bookings.cancel(user.getId(), cancelled.id());
    // Legacy weeks can have unpublished or incomplete schedules.
    jdbc.update("update booking_week set published=false where week_start=?", WEEK);
    jdbc.update("delete from booking_slot where id not in (select slot_id from active_slot_claim)");
    when(clock.instant()).thenReturn(WEEK.plusWeeks(1).atStartOfDay(BookingRules.SEOUL).toInstant());
    var session = login(user);
    var response = request(session.client(), "GET", "/weeks/" + WEEK + "/timetable", null, null);
    assertEquals(200, response.statusCode());
    var table = json.readValue(response.body(), Timetable.class);
    assertEquals(189, table.slots().size());
    var booked = table.slots().stream().filter(s -> "BOOKED".equals(s.status())).toList();
    assertEquals(2, booked.size());
    assertTrue(booked.stream().allMatch(s -> reservation.id().equals(s.reservationId())
        && user.getName().equals(s.memberName()) && "정기 합주".equals(s.rehearsalName())
        && s.reservationType() == ReservationType.TEAM_REHEARSAL && s.mine()));
    assertTrue(table.slots().stream().noneMatch(s -> "AVAILABLE".equals(s.status())));
    assertEquals("BOOKING_CLOSED", assertThrows(BusinessException.class,
        () -> reserve(user, "historical-new", 630, 660)).code);
    assertThrows(BusinessException.class, () -> bookings.cancel(user.getId(), reservation.id()));
    int weekCount = count("booking_week"), slotCount = count("booking_slot");
    var empty = bookings.timetable(user.getId(), WEEK.minusWeeks(2));
    assertEquals(189, empty.slots().size());
    assertTrue(empty.slots().stream().allMatch(s -> "UNAVAILABLE".equals(s.status())
        && s.id() == null && s.reservationId() == null));
    assertEquals(weekCount, count("booking_week"));
    assertEquals(slotCount, count("booking_slot"));
    var pending = member("past-wait", false, false);
    assertTrue(bookings.timetable(pending.getId(), WEEK).slots().stream()
        .allMatch(s -> s.memberName() == null && s.rehearsalName() == null));
    assertTrue(bookings.timetable(user.getId(), bookings.bookingWeek().plusWeeks(1)).slots().isEmpty());
  }

  @Test
  void legacyWeekIsPreviewedWithoutWritesAndReconciledOnBookingWithoutLosingReservations() {
    var original = reserve(user, "legacy-week", 540, 600);
    jdbc.update("update booking_week set automatic=false,published=false");
    jdbc.update("delete from booking_slot where id not in (select slot_id from active_slot_claim)");
    jdbc.update("update booking_slot set blocked=true,block_reason='점검'");
    jdbc.update(
        "insert into booking_slot(week_id,start_at,end_at) values(?,?,?)",
        weeks.findByWeekStart(WEEK).orElseThrow().getId(),
        java.sql.Timestamp.from(at(1350)), java.sql.Timestamp.from(at(1380)));
    var storedWeek = jdbc.queryForList("select * from booking_week");
    var storedSlots = jdbc.queryForList("select * from booking_slot order by id");
    var table = bookings.bookingTimetable(user.getId());
    assertEquals(189, table.slots().size());
    assertTrue(table.published());
    assertEquals(NOW, table.opensAt());
    assertEquals(NOW.plusSeconds(7 * 86400), table.closesAt());
    assertEquals(2, table.slots().stream().filter(slot -> original.id().equals(slot.reservationId())).count());
    assertEquals(2, count("active_slot_claim"));
    assertTrue(table.slots().stream().allMatch(slot -> slot.blockReason() == null));
    var listed = bookings.weeks(user.getId(), WEEK, WEEK);
    assertEquals(1, listed.size());
    assertTrue(listed.getFirst().published());
    assertEquals(storedWeek, jdbc.queryForList("select * from booking_week"));
    assertEquals(storedSlots, jdbc.queryForList("select * from booking_slot order by id"));
    assertEquals("SLOT_UNAVAILABLE",
        assertThrows(BusinessException.class, () -> reserve(user, "too-late", 1350, 1380)).code);
    reserve(user, "last-slot", 1320, 1350);
    assertTrue(weeks.findByWeekStart(WEEK).orElseThrow().getAutomatic());
    assertEquals(190, count("booking_slot")); // Preserve legacy rows, hide unreserved hours outside 09:00-22:30.
    assertEquals(2, bookings.bookingTimetable(user.getId()).slots().stream()
        .filter(slot -> original.id().equals(slot.reservationId())).count());
  }

  @Test
  void legacyReservationsOutsideFixedHoursRemainVisibleBeforeAndAfterReconciliation() {
    var original = reserve(user, "legacy-outside-hours", 540, 570);
    jdbc.update("update booking_week set automatic=false,published=false");
    jdbc.update("update reservation set start_at=?,end_at=? where id=?",
        java.sql.Timestamp.from(at(1350)), java.sql.Timestamp.from(at(1380)), original.id());
    jdbc.update("update booking_slot set start_at=?,end_at=? where id in "
            + "(select slot_id from active_slot_claim where reservation_id=?)",
        java.sql.Timestamp.from(at(1350)), java.sql.Timestamp.from(at(1380)), original.id());
    var storedWeek = jdbc.queryForList("select * from booking_week");
    var storedSlots = jdbc.queryForList("select * from booking_slot order by id");
    var preview = bookings.bookingTimetable(user.getId());
    var booked = preview.slots().stream()
        .filter(s -> original.id().equals(s.reservationId())).findFirst().orElseThrow();
    assertEquals(at(1350), booked.startAt());
    assertEquals("BOOKED", booked.status());
    assertEquals("운영 시간 외", booked.blockReason());
    assertTrue(booked.mine());
    assertEquals(storedWeek, jdbc.queryForList("select * from booking_week"));
    assertEquals(storedSlots, jdbc.queryForList("select * from booking_slot order by id"));
    reserve(user, "reconcile", 600, 630);
    assertTrue(bookings.bookingTimetable(user.getId()).slots().contains(booked));
    assertEquals(original.id(), bookings.get(user.getId(), original.id()).id());
    assertEquals(2, count("active_slot_claim"));
  }

  @Test
  void reservationTypesRoundTripThroughApiTimetableAndDiscordAndShareSlots() throws Exception {
    var session = login(user);
    int start = 540;
    var created = new ArrayList<ReservationView>();
    for (var type : ReservationType.values()) {
      var input = new BookingInput(WEEK, at(start), at(start + 30), type.label(), type);
      var body = Map.of("weekStart", WEEK.toString(), "startAt", input.startAt().toString(),
          "endAt", input.endAt().toString(), "rehearsalName", input.rehearsalName(),
          "reservationType", type.name());
      var request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/api/v1/reservations"))
          .header("Content-Type", "application/json").header("X-CSRF-TOKEN", session.csrf())
          .header("Idempotency-Key", "type-" + type.name())
          .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body))).build();
      var response = session.client().send(request, HttpResponse.BodyHandlers.ofString());
      assertEquals(201, response.statusCode(), response.body());
      assertEquals(type.name(), json.readTree(response.body()).path("reservationType").asString());
      long id = json.readTree(response.body()).path("id").asLong();
      String location = "/api/v1/reservations/" + id;
      assertEquals(location, response.headers().firstValue("Location").orElseThrow());
      var replay = session.client().send(request, HttpResponse.BodyHandlers.ofString());
      assertEquals(200, replay.statusCode(), replay.body());
      assertEquals(id, json.readTree(replay.body()).path("id").asLong());
      assertEquals(location, replay.headers().firstValue("Location").orElseThrow());
      var fetched = request(session.client(), "GET", "/reservations/" + id, null, null);
      assertEquals(200, fetched.statusCode(), fetched.body());
      assertEquals(id, json.readTree(fetched.body()).path("id").asLong());
      var reservation = bookings.reserve(user.getId(), "type-" + type.name(), input).reservation();
      created.add(reservation);
      assertEquals(type, bookings.get(user.getId(), reservation.id()).reservationType());
      assertEquals(type, bookings.timetable(user.getId(), WEEK).slots().stream()
          .filter(slot -> reservation.id().equals(slot.reservationId())).findFirst().orElseThrow().reservationType());
      start += 30;
    }
    assertEquals(Set.of(ReservationType.values()),
        new HashSet<>(bookings.mine(user.getId(), 0, 20, "all").content().stream()
            .map(ReservationView::reservationType).toList()));
    var summary = announcements.message(WEEK.minusDays(1)).schedule();
    for (var type : ReservationType.values()) assertTrue(summary.contains(type.label()));
    assertEquals("IDEMPOTENCY_MISMATCH",
        assertThrows(BusinessException.class, () -> bookings.reserve(user.getId(), "type-TEAM_REHEARSAL",
            new BookingInput(WEEK, at(540), at(570), "팀 합주", ReservationType.LESSON))).code);
    assertEquals("SLOT_TAKEN",
        assertThrows(BusinessException.class, () -> bookings.reserve(user.getId(), "different-type-same-slot",
            new BookingInput(WEEK, at(540), at(570), "개인 연습", ReservationType.PERSONAL_PRACTICE))).code);
    var invalid = new HashMap<String, Object>(Map.of("weekStart", WEEK.toString(),
        "startAt", at(540).toString(), "endAt", at(570).toString(), "rehearsalName", "예약"));
    assertEquals(400, request(session.client(), "POST", "/reservations", invalid, session.csrf()).statusCode());
    invalid.put("reservationType", "OTHER");
    assertEquals(400, request(session.client(), "POST", "/reservations", invalid, session.csrf()).statusCode());
    var violation = assertThrows(org.springframework.dao.DataAccessException.class,
        () -> jdbc.update("update reservation set reservation_type='OTHER' where id=?", created.getFirst().id()));
    assertTrue(violation.getMostSpecificCause().getMessage().contains("ck_reservation_type"));
    bookings.cancel(user.getId(), created.getFirst().id());
    assertEquals(ReservationType.TEAM_REHEARSAL, bookings.get(user.getId(), created.getFirst().id()).reservationType());
    assertNull(bookings.timetable(user.getId(), WEEK).slots().getFirst().reservationType());
  }

  @Test
  void fiftyMembersCompeteForOneRange() throws Exception {
    var contenders = new ArrayList<Member>();
    for (int i = 0; i < 50; i++) contenders.add(member("member" + i, true, false));
    try (var pool = Executors.newFixedThreadPool(50)) {
      var ready = new CountDownLatch(50);
      var go = new CountDownLatch(1);
      var successes = new AtomicInteger();
      var conflicts = new AtomicInteger();
      var tasks = new ArrayList<Future<?>>();
      for (var m : contenders)
        tasks.add(
            pool.submit(
                () -> {
                  ready.countDown();
                  try {
                    go.await();
                    reserve(m, UUID.randomUUID().toString(), 540, 600);
                    successes.incrementAndGet();
                  } catch (BusinessException e) {
                    assertEquals("SLOT_TAKEN", e.code);
                    conflicts.incrementAndGet();
                  } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException(e);
                  }
                }));
      assertTrue(ready.await(10, TimeUnit.SECONDS));
      go.countDown();
      for (var task : tasks) task.get(30, TimeUnit.SECONDS);
      assertEquals(1, successes.get());
      assertEquals(49, conflicts.get());
      assertEquals(1, count("reservation"));
      assertEquals(2, count("active_slot_claim"));
    }
  }

  @Test
  void retriesPartialOverlapAndWholeRollback() throws Exception {
    var r = reserve(user, "same-key", 570, 630);
    assertEquals(r.id(), reserve(user, "same-key", 570, 630).id());
    assertThrows(
        BusinessException.class,
        () -> bookings.reserve(user.getId(), "same-key", input(600, 660, "정기 합주")));
    assertThrows(BusinessException.class, () -> reserve(admin, "overlap", 540, 600));
    assertEquals(1, count("reservation"));
    assertEquals(2, count("active_slot_claim"));
    // The primary key is a final invariant even when bypassing application code.
    assertThrows(
        org.springframework.dao.DataIntegrityViolationException.class,
        () ->
            jdbc.update(
                "insert into active_slot_claim select slot_id,reservation_id from active_slot_claim"
                    + " limit 1"));
  }

  @Test
  void concurrentIdenticalRequestsReturnOneReservation() throws Exception {
    try (var pool = Executors.newFixedThreadPool(10)) {
      var tasks = new ArrayList<Future<Long>>();
      for (int i = 0; i < 10; i++)
        tasks.add(pool.submit(() -> reserve(user, "retry", 540, 600).id()));
      var ids = new HashSet<Long>();
      for (var task : tasks) ids.add(task.get(20, TimeUnit.SECONDS));
      assertEquals(1, ids.size());
      assertEquals(1, count("reservation"));
    }
  }

  @Test
  void cancellationRebookingAndOwnership() {
    var r = reserve(user, "one", 540, 600);
    assertEquals(
        Type.NOT_FOUND,
        assertThrows(
                BusinessException.class, () -> bookings.cancel(admin.getId(), r.id()))
            .type);
    bookings.cancel(user.getId(), r.id());
    assertEquals(0, count("active_slot_claim"));
    assertEquals(
        "ALREADY_CANCELLED",
        assertThrows(
                BusinessException.class, () -> bookings.cancel(user.getId(), r.id()))
            .code);
    var next = reserve(admin, "two", 540, 600);
    assertNotEquals(r.id(), next.id());
    assertThrows(BusinessException.class, () -> bookings.cancel(user.getId(), r.id()));
    assertEquals(2, count("active_slot_claim"));
    assertEquals("CANCELLED", reserve(user, "one", 540, 600).status());
    assertEquals(2, count("reservation"));
    assertEquals(2, count("active_slot_claim"));
    bookings.cancel(admin.getId(), next.id());
    assertEquals(0, count("active_slot_claim"));
  }

  @Test
  void bookingWindowAndStartedCancellation() {
    when(clock.instant()).thenReturn(NOW.minusSeconds(1));
    assertThrows(BusinessException.class, () -> reserve(user, "early", 540, 600));
    when(clock.instant()).thenReturn(NOW.plusSeconds(7 * 86400));
    assertThrows(BusinessException.class, () -> reserve(user, "closed", 540, 600));
    when(clock.instant()).thenReturn(NOW);
    var r = reserve(user, "ok", 540, 600);
    when(clock.instant()).thenReturn(at(540));
    assertThrows(BusinessException.class, () -> bookings.cancel(user.getId(), r.id()));
    assertThrows(BusinessException.class, () -> reserve(user, "started", 540, 600));
  }

  @Test
  void requestWaitingForWeekLockUsesTimeAfterAcquiringIt() throws Exception {
    try (var pool = Executors.newFixedThreadPool(2)) {
      var locked = new CountDownLatch(1);
      var release = new CountDownLatch(1);
      var holder =
          pool.submit(
              () ->
                  tx.executeWithoutResult(
                      s -> {
                        weeks.locked(WEEK).orElseThrow();
                        locked.countDown();
                        try {
                          release.await(5, TimeUnit.SECONDS);
                        } catch (InterruptedException e) {
                          Thread.currentThread().interrupt();
                          throw new RuntimeException(e);
                        }
                      }));
      assertTrue(locked.await(5, TimeUnit.SECONDS));
      var waiting = pool.submit(() -> reserve(user, "wait-close", 540, 600));
      try {
        assertThrows(TimeoutException.class, () -> waiting.get(150, TimeUnit.MILLISECONDS));
        when(clock.instant()).thenReturn(NOW.plusSeconds(7 * 86400));
      } finally {
        release.countDown();
      }
      holder.get(5, TimeUnit.SECONDS);
      var error = assertThrows(ExecutionException.class, () -> waiting.get(5, TimeUnit.SECONDS));
      assertEquals(Type.CONFLICT, ((BusinessException) error.getCause()).type);
      assertEquals(0, count("reservation"));
    }
  }

  @Test
  void cancellationRacingWithNewReservationCannotDeleteTheNewClaim() throws Exception {
    var original = reserve(user, "old", 540, 600);
    try (var pool = Executors.newFixedThreadPool(2)) {
      var go = new CountDownLatch(1);
      var cancel =
          pool.submit(
              () -> {
                go.await();
                return bookings.cancel(user.getId(), original.id());
              });
      var create =
          pool.submit(
              () -> {
                go.await();
                try {
                  return reserve(admin, "new", 540, 600);
                } catch (BusinessException e) {
                  assertEquals("SLOT_TAKEN", e.code);
                  return null;
                }
              });
      go.countDown();
      cancel.get(10, TimeUnit.SECONDS);
      var next = create.get(10, TimeUnit.SECONDS);
      if (next == null) next = reserve(admin, "new", 540, 600);
      assertEquals(2, count("active_slot_claim"));
      assertEquals(
          next.id(),
          jdbc.queryForObject("select distinct reservation_id from active_slot_claim", Long.class));
    }
  }

  @Test
  void reservationWaitsForMembershipChangeAndRejectsExpelledMember() throws Exception {
    var locked = new CountDownLatch(1);
    var release = new CountDownLatch(1);
    try (var pool = Executors.newVirtualThreadPerTaskExecutor()) {
      var holder = pool.submit(() -> tx.executeWithoutResult(status -> {
        var m = members.locked(user.getId()).orElseThrow();
        m.expel(admin.getId(), "자격 해제", NOW, 1);
        locked.countDown();
        try {
          if (!release.await(5, TimeUnit.SECONDS)) throw new IllegalStateException("Lock timed out");
        } catch (InterruptedException e) {
          Thread.currentThread().interrupt();
          throw new IllegalStateException(e);
        }
      }));
      assertTrue(locked.await(5, TimeUnit.SECONDS));
      var waiting = pool.submit(() -> reserve(user, "expulsion-race", 540, 570));
      try {
        assertThrows(TimeoutException.class, () -> waiting.get(150, TimeUnit.MILLISECONDS));
      } finally {
        release.countDown();
      }
      holder.get(5, TimeUnit.SECONDS);
      var error = assertThrows(ExecutionException.class, () -> waiting.get(5, TimeUnit.SECONDS));
      assertEquals(Type.FORBIDDEN, ((BusinessException) error.getCause()).type);
      assertEquals(0, count("reservation"));
      assertEquals(0, count("active_slot_claim"));
    }
  }

  @Test
  void timetableUsesOneProjectionQueryRegardlessOfSlotCount() {
    reserve(user, "query", 540, 600);
    var stats = emf.unwrap(org.hibernate.SessionFactory.class).getStatistics();
    stats.clear();
    var table = bookings.timetable(user.getId(), WEEK);
    assertEquals(189, table.slots().size());
    assertEquals(
        3, stats.getPrepareStatementCount()); // member + week + one projection; no slot/member N+1.
    assertEquals(1, bookings.mine(user.getId(), 0, 20, "all").content().size());
    var notApproved = member("pending", false, false);
    assertTrue(
        bookings.timetable(notApproved.getId(), WEEK).slots().stream()
            .allMatch(s -> s.memberName() == null && s.rehearsalName() == null));
  }

  @Test
  void listResponsesKeepFlatPaginationMetadataForEmptyAndPopulatedPages() throws Exception {
    var session = login(admin);
    String[] paths = {"/members/me/reservations", "/admin/members"};
    for (String path : paths) {
      var response = request(session.client(), "GET", path + "?size=1", null, null);
      assertEquals(200, response.statusCode(), response.body());
      var page = json.readTree(response.body());
      assertTrue(page.path("content").isArray());
      assertEquals(0, page.path("number").asInt());
      assertEquals(1, page.path("size").asInt());
      assertEquals(path.equals(paths[0]) ? 0 : 2, page.path("totalElements").asLong());
      assertEquals(path.equals(paths[0]) ? 0 : 2, page.path("totalPages").asInt());
      assertEquals(path.equals(paths[0]), page.path("last").asBoolean());
      assertEquals(path.equals(paths[0]), page.path("empty").asBoolean());
      assertFalse(page.has("pageable"));
    }
    reserve(admin, "pagination", 540, 600);
    var response = request(session.client(), "GET", paths[0] + "?size=1", null, null);
    assertEquals(200, response.statusCode(), response.body());
    var page = json.readTree(response.body());
    assertEquals(1, page.path("totalElements").asLong());
    assertEquals("TEAM_REHEARSAL", page.path("content").get(0).path("reservationType").asString());
    assertTrue(page.path("content").get(0).path("startAt").isString());
    assertTrue(page.path("last").asBoolean());
    var pastEnd = request(session.client(), "GET", paths[0] + "?page=1&size=1", null, null);
    assertEquals(200, pastEnd.statusCode(), pastEnd.body());
    var emptyPage = json.readTree(pastEnd.body());
    assertEquals(1, emptyPage.path("number").asInt());
    assertTrue(emptyPage.path("empty").asBoolean());
    assertTrue(emptyPage.path("last").asBoolean());
    assertEquals(1, emptyPage.path("totalElements").asLong());
  }

  @Test
  void reservationNameLengthAndJsonTypesAreEnforcedAtHttpBoundary() throws Exception {
    var session = login(user);
    var bodies = new ArrayList<Map<String, Object>>();
    for (Object name : new Object[] {"가".repeat(20), "가".repeat(21), "\u200b", "예약\n이름", 123})
      bodies.add(new HashMap<>(Map.of("weekStart", WEEK.toString(), "startAt", at(540).toString(),
          "endAt", at(570).toString(), "rehearsalName", name, "reservationType", "TEAM_REHEARSAL")));
    var numericType = new HashMap<>(bodies.getFirst());
    numericType.put("reservationType", 0);
    bodies.add(numericType);
    var missingDate = new HashMap<>(bodies.getFirst());
    missingDate.remove("startAt");
    bodies.add(missingDate);
    for (int i = 0; i < bodies.size(); i++) {
      var httpRequest = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/api/v1/reservations"))
          .header("Content-Type", "application/json").header("X-CSRF-TOKEN", session.csrf())
          .header("Idempotency-Key", "validation-" + i)
          .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(bodies.get(i)))).build();
      var response = session.client().send(httpRequest, HttpResponse.BodyHandlers.ofString());
      assertEquals(i == 0 ? 201 : 400, response.statusCode(), response.body());
    }
    assertEquals(1, count("reservation"));
  }
}
