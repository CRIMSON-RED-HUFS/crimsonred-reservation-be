package com.crimsonred.reservation.booking.domain;

import static org.junit.jupiter.api.Assertions.*;

import com.crimsonred.reservation.auth.application.AuthService;
import com.crimsonred.reservation.shared.domain.BusinessException;
import java.time.*;
import org.junit.jupiter.api.Test;

class BookingRulesTest {
  final LocalDate week = LocalDate.of(2027, 1, 4);

  Instant at(int minute) {
    return week.atStartOfDay(BookingRules.SEOUL).plusMinutes(minute).toInstant();
  }

  @Test
  void halfHourAndMidnight() {
    assertThrows(BusinessException.class, () -> BookingRules.monday(null));
    assertThrows(BusinessException.class, () -> BookingRules.range(week, null, at(600)));
    assertThrows(BusinessException.class, () -> BookingRules.range(week, at(540), null));
    assertDoesNotThrow(() -> BookingRules.range(week, at(1410), at(1440)));
    assertThrows(BusinessException.class, () -> BookingRules.range(week, at(540), at(541)));
    assertThrows(BusinessException.class, () -> BookingRules.range(week, at(1410), at(1470)));
    assertThrows(BusinessException.class, () -> BookingRules.range(week, at(600), at(540)));
    assertThrows(
        BusinessException.class, () -> BookingRules.range(week, at(540).plusNanos(1), at(600)));
    assertThrows(BusinessException.class, () -> BookingRules.range(week, at(-30), at(0)));
    assertThrows(BusinessException.class, () -> BookingRules.monday(week.plusDays(1)));
  }

  @Test
  void exactBookingWindowBoundaries() {
    var opensAt = WeeklySchedule.opensAt(week);
    var w = BookingWeek.create(week, opensAt, opensAt.plusSeconds(60), true);
    assertThrows(
        BusinessException.class, () -> BookingRules.open(w, w.getOpensAt().minusNanos(1), at(540)));
    assertDoesNotThrow(() -> BookingRules.open(w, w.getOpensAt(), at(540)));
    assertDoesNotThrow(() -> BookingRules.open(w, w.getClosesAt().minusNanos(1), at(540)));
    assertThrows(BusinessException.class, () -> BookingRules.open(w, w.getClosesAt(), at(540)));
    assertThrows(
        BusinessException.class, () -> BookingRules.open(w, w.getOpensAt(), w.getOpensAt()));
  }

  @Test
  void fixedHoursAndSunday23BookingRollover() {
    var schedule = new WeeklySchedule(540, 1350);
    var starts = schedule.starts(week);
    assertEquals(189, starts.size());
    assertEquals(at(540), starts.iterator().next());
    assertTrue(starts.contains(at(6 * 1440 + 1320)));
    assertFalse(starts.contains(at(1440)));
    assertFalse(starts.contains(at(1350)));
    assertFalse(starts.contains(at(510)));
    Instant sunday23 = WeeklySchedule.closesAt(week);
    assertEquals(Instant.parse("2027-01-03T14:00:00Z"), sunday23);
    assertEquals(week, WeeklySchedule.bookingWeek(sunday23.minusNanos(1)));
    assertEquals(week.plusWeeks(1), WeeklySchedule.bookingWeek(sunday23));
    assertEquals(week.plusWeeks(1), WeeklySchedule.bookingWeek(sunday23.plusSeconds(3600)));
    var w = BookingWeek.create(week, WeeklySchedule.opensAt(week), sunday23, true);
    assertDoesNotThrow(() -> BookingRules.open(w, sunday23.minusNanos(1), at(540)));
    assertThrows(BusinessException.class, () -> BookingRules.open(w, sunday23, at(540)));
    assertThrows(IllegalArgumentException.class, () -> new WeeklySchedule(541, 1440));
    assertThrows(IllegalArgumentException.class, () -> new WeeklySchedule(0, 1441));
    assertThrows(IllegalArgumentException.class, () -> new WeeklySchedule(600, 540));
  }

  @Test
  void exactEmailDomain() {
    assertEquals("student@hufs.ac.kr", AuthService.normalize(" Student@HUFS.AC.KR "));
    for (var email :
        new String[] {
          "student@hufs.ac.kr.evil.com",
          "student@evil.hufs.ac.kr",
          "student@gmail.com",
          "a@@hufs.ac.kr",
          "a b@hufs.ac.kr"
        }) assertThrows(BusinessException.class, () -> AuthService.normalize(email));
  }
}
