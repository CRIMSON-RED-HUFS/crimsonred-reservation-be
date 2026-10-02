package com.crimsonred.reservation.booking.domain;

import com.crimsonred.reservation.shared.domain.BusinessException;
import java.time.*;

public class BookingWeek {
  private Long id;
  private LocalDate weekStart;
  private boolean published;
  private Instant opensAt;
  private Instant closesAt;
  private boolean automatic;

  public boolean getAutomatic() {
    return automatic;
  }

  public void useWeeklySchedule() {
    update(WeeklySchedule.opensAt(weekStart), WeeklySchedule.closesAt(weekStart), true);
    automatic = true;
  }

  public Long getId() {
    return id;
  }

  public LocalDate getWeekStart() {
    return weekStart;
  }

  public boolean getPublished() {
    return published;
  }

  public Instant getOpensAt() {
    return opensAt;
  }

  public Instant getClosesAt() {
    return closesAt;
  }

  protected BookingWeek() {}

  public static BookingWeek create(
      LocalDate weekStart, Instant opensAt, Instant closesAt, boolean published) {
    BookingRules.monday(weekStart);
    var week = new BookingWeek();
    week.weekStart = weekStart;
    week.update(opensAt, closesAt, published);
    return week;
  }

  private void update(Instant opensAt, Instant closesAt, boolean published) {
    if (!opensAt.isBefore(closesAt)) throw BusinessException.bad("접수 시작은 마감보다 빨라야 합니다.");
    this.opensAt = opensAt;
    this.closesAt = closesAt;
    this.published = published;
  }
}
