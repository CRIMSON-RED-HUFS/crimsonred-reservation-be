package com.crimsonred.reservation.booking.domain;

public enum ReservationType {
  TEAM_REHEARSAL("팀 합주"),
  PERSONAL_PRACTICE("개인 연습"),
  LESSON("레슨");

  private final String label;

  ReservationType(String label) {
    this.label = label;
  }

  public String label() {
    return label;
  }
}
