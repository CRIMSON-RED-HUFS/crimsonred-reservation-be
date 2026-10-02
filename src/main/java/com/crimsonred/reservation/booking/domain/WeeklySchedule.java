package com.crimsonred.reservation.booking.domain;

import java.time.*;
import java.time.temporal.TemporalAdjusters;
import java.util.LinkedHashSet;
import java.util.Set;

/** The same opening hours apply Monday through Sunday, in Seoul time. */
public record WeeklySchedule(int startMinute, int endMinute) {
  public WeeklySchedule {
    if (startMinute < 0 || endMinute > 1440 || startMinute >= endMinute
        || startMinute % 30 != 0 || endMinute % 30 != 0)
      throw new IllegalArgumentException("이용 시간은 0~1440분 사이의 30분 단위여야 합니다.");
  }

  /** Book the upcoming Monday-Sunday; advance to the following week at Sunday 23:00. */
  public static LocalDate bookingWeek(Instant now) {
    var local = now.atZone(BookingRules.SEOUL);
    var monday = local.toLocalDate().with(TemporalAdjusters.next(DayOfWeek.MONDAY));
    return local.getDayOfWeek() == DayOfWeek.SUNDAY && local.getHour() >= 23
        ? monday.plusWeeks(1) : monday;
  }

  public static Instant opensAt(LocalDate week) {
    return closesAt(week.minusWeeks(1));
  }

  public static Instant closesAt(LocalDate week) {
    return week.atStartOfDay(BookingRules.SEOUL).minusHours(1).toInstant();
  }

  public Set<Instant> starts(LocalDate week) {
    BookingRules.monday(week);
    var starts = new LinkedHashSet<Instant>();
    for (int day = 0; day < 7; day++)
      for (int minute = startMinute; minute < endMinute; minute += 30)
        starts.add(week.plusDays(day).atStartOfDay(BookingRules.SEOUL)
            .plusMinutes(minute).toInstant());
    return starts;
  }
}
