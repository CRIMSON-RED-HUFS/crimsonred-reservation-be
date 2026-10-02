package com.crimsonred.reservation.booking.domain;

import com.crimsonred.reservation.shared.domain.BusinessException;
import java.time.*;

public final class BookingRules {
  public static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

  private BookingRules() {}

  public static void monday(LocalDate date) {
    if (date == null || date.getDayOfWeek() != DayOfWeek.MONDAY)
      throw BusinessException.bad("주차 시작일은 월요일이어야 합니다.");
  }

  public static void range(LocalDate week, Instant start, Instant end) {
    monday(week);
    if (start == null || end == null) throw BusinessException.bad("시작·종료 시간을 선택해주세요.");
    var s = start.atZone(SEOUL);
    var e = end.atZone(SEOUL);
    if (!start.isBefore(end)
        || start.getNano() != 0
        || end.getNano() != 0
        || s.getSecond() != 0
        || e.getSecond() != 0
        || s.getMinute() % 30 != 0
        || e.getMinute() % 30 != 0) throw BusinessException.bad("30분 단위의 시작·종료 시간을 선택해주세요.");
    if (s.toLocalDate().isBefore(week) || !s.toLocalDate().isBefore(week.plusDays(7)))
      throw BusinessException.bad("해당 주차의 시간을 선택해주세요.");
    if (!s.toLocalDate().equals(e.toLocalDate())
        && !(e.toLocalDate().equals(s.toLocalDate().plusDays(1))
            && e.toLocalTime().equals(LocalTime.MIDNIGHT)))
      throw BusinessException.bad("같은 날짜의 연속 구간만 예약할 수 있습니다.");
  }

  public static void open(BookingWeek w, Instant now, Instant start) {
    if (!w.getWeekStart().equals(WeeklySchedule.bookingWeek(now))
        || !w.getPublished() || now.isBefore(w.getOpensAt()) || !now.isBefore(w.getClosesAt()))
      throw BusinessException.conflict("BOOKING_CLOSED", "예약 접수 기간이 아닙니다.");
    if (!now.isBefore(start))
      throw BusinessException.conflict("ALREADY_STARTED", "이용 시작 전까지만 예약할 수 있습니다.");
  }
}
