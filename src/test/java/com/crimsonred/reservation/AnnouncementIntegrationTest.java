package com.crimsonred.reservation;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.crimsonred.reservation.announcement.application.AnnouncementService;
import com.crimsonred.reservation.announcement.application.port.AnnouncementSender;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

class AnnouncementIntegrationTest extends IntegrationTestSupport {
  @Test
  void unexpectedSenderFailureIsUnknownAndNotRetried() {
    when(webhook.send(anyString(), anyString())).thenThrow(new IllegalStateException("Response lost"));
    announcements.enqueue(LocalDate.of(2027, 1, 3));
    announcements.pump();
    assertEquals("UNKNOWN", jdbc.queryForObject("select status from announcement_delivery", String.class));
    when(clock.instant()).thenReturn(NOW.plusSeconds(600));
    announcements.pump();
    verify(webhook, times(1)).send(anyString(), anyString());
  }

  @Test
  void dueNoticesExcludeTerminalFailuresAndLimitTheBatch() {
    for (int i = 0; i < 55; i++) announcements.enqueue(LocalDate.of(2027, 1, 3).plusWeeks(i));
    assertEquals(50, deliveries.findDue(NOW).size());
    jdbc.update("update announcement_delivery set status='FAILED',attempts=3");
    assertTrue(deliveries.findDue(NOW).isEmpty());
    jdbc.update("update announcement_delivery set status='UNKNOWN',attempts=1");
    assertTrue(deliveries.findDue(NOW).isEmpty());
    jdbc.update("update announcement_delivery set status='PENDING',attempts=0,next_attempt_at=?",
        java.sql.Timestamp.from(NOW.plusSeconds(1)));
    assertTrue(deliveries.findDue(NOW).isEmpty());
  }

  @Test
  void announcementUsesUpcomingUsageWeekEvenAfterBookingRollover() {
    reserve(user, "notice-booking", 540, 600);
    announcements.enqueue(LocalDate.of(2027, 1, 3));
    announcements.enqueue(LocalDate.of(2027, 1, 3));
    assertEquals(1, count("announcement_delivery"));
    announcements.pump();
    assertEquals(
        "FAILED", jdbc.queryForObject("select status from announcement_delivery", String.class));
    when(clock.instant()).thenReturn(Instant.parse("2027-01-03T14:00:00Z"));
    assertEquals(WEEK.plusWeeks(1), bookings.bookingTimetable(user.getId()).weekStart());
    var msg = announcements.message(LocalDate.of(2027, 1, 3));
    assertTrue(msg.content().contains("2027-01-04"));
    assertTrue(msg.content().startsWith("🎸 이번 주 동아리방 예약 현황"));
    assertTrue(msg.content().contains("2027-01-04 ~ 2027-01-10"));
    assertTrue(msg.content().contains("일요일 23:00"));
    assertFalse(msg.content().contains("/?week=2027-01-11"));
    assertTrue(msg.schedule().contains(user.getName()));
    assertTrue(msg.schedule().contains("09:00–10:00"));
    assertTrue(msg.schedule().contains("예약 없음"));
  }

  @Test
  void announcementClaimIsAtomicAndUnknownIsNeverAutomaticallyRetried() throws Exception {
    when(webhook.send(anyString(), anyString()))
        .thenReturn(new AnnouncementSender.Result("UNKNOWN", null, 0, "응답 유실"));
    announcements.enqueue(LocalDate.of(2027, 1, 3));
    try (var pool = Executors.newFixedThreadPool(4)) {
      var tasks = new ArrayList<Future<?>>();
      for (int i = 0; i < 4; i++) tasks.add(pool.submit(announcements::pump));
      for (var task : tasks) task.get(5, TimeUnit.SECONDS);
    }
    verify(webhook, times(1)).send(anyString(), anyString());
    announcements.pump();
    assertEquals(
        "UNKNOWN", jdbc.queryForObject("select status from announcement_delivery", String.class));
    verify(webhook, times(1)).send(anyString(), anyString());
  }

  @Test
  void announcementRetriesRespectDueTimeAndStopAtThreeAttempts() {
    when(webhook.send(anyString(), anyString()))
        .thenReturn(new AnnouncementSender.Result("FAILED", null, 75, "요청 제한"));
    announcements.enqueue(LocalDate.of(2027, 1, 3));
    announcements.pump();
    when(clock.instant()).thenReturn(NOW.plusSeconds(74));
    announcements.pump();
    verify(webhook, times(1)).send(anyString(), anyString());
    when(clock.instant()).thenReturn(NOW.plusSeconds(75));
    announcements.pump();
    when(clock.instant()).thenReturn(NOW.plusSeconds(150));
    announcements.pump();
    when(clock.instant()).thenReturn(NOW.plusSeconds(600));
    announcements.pump();
    verify(webhook, times(3)).send(anyString(), anyString());
    assertEquals(
        3, jdbc.queryForObject("select attempts from announcement_delivery", Integer.class));
  }

  @Test
  void restartRecoversLatestDueNoticeAndFlagsAbandonedDelivery() {
    var service =
        new AnnouncementService(
            deliveries,
            weeks,
            slots,
            webhook,
            clock,
            true,
            "http://localhost:3000///",
            org.springframework.scheduling.support.CronExpression.parse("0 0 23 * * SUN")::next);
    when(webhook.send(anyString(), anyString()))
        .thenReturn(new AnnouncementSender.Result("SENT", "discord-1", 0, null));
    when(clock.instant()).thenReturn(Instant.parse("2027-01-03T14:01:00Z"));
    service.recoverAndPump();
    service.recoverAndPump();
    assertEquals(1, count("announcement_delivery"));
    assertEquals(
        "SENT", jdbc.queryForObject("select status from announcement_delivery", String.class));
    verify(webhook, times(1)).send(contains("http://localhost:3000/?week=2027-01-04"), anyString());
    jdbc.update(
        "update announcement_delivery set status='SENDING',started_at=?",
        java.sql.Timestamp.from(NOW.minusSeconds(7 * 86400)));
    service.recoverAndPump();
    assertEquals(
        "UNKNOWN", jdbc.queryForObject("select status from announcement_delivery", String.class));
    assertEquals(
        "발송 중 서버 중단: 채널 확인이 필요합니다.",
        jdbc.queryForObject("select error_summary from announcement_delivery", String.class));
    verify(webhook, times(1)).send(anyString(), anyString());
  }
}
