package com.crimsonred.reservation.booking.domain;

public class ActiveSlotClaim {
  private Long slotId;
  private Long reservationId;

  public ActiveSlotClaim() {}

  public ActiveSlotClaim(Long slotId, Long reservationId) {
    this.slotId = slotId;
    this.reservationId = reservationId;
  }
}
