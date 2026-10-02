package com.crimsonred.reservation.booking.domain;

import java.time.Instant;

public class BookingSlot {
  private Long id;
  private Long weekId;
  private Instant startAt;
  private Instant endAt;
  private boolean blocked;
  private String blockReason;

  public Long getId() {
    return id;
  }

  public Long getWeekId() {
    return weekId;
  }

  public Instant getStartAt() {
    return startAt;
  }

  public Instant getEndAt() {
    return endAt;
  }

  public boolean getBlocked() {
    return blocked;
  }

  protected BookingSlot() {}

  public static BookingSlot create(Long weekId, Instant startAt) {
    var slot = new BookingSlot();
    slot.weekId = weekId;
    slot.startAt = startAt;
    slot.endAt = startAt.plusSeconds(1800);
    return slot;
  }

  public void changeBlocked(boolean blocked, String reason) {
    this.blocked = blocked;
    this.blockReason = blocked ? reason : null;
  }
}
