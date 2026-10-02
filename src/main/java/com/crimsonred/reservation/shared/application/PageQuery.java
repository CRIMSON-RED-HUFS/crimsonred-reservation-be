package com.crimsonred.reservation.shared.application;

public record PageQuery(int number, int size, String sortBy) {
  public PageQuery {
    number = Math.max(0, number);
    size = Math.clamp(size, 1, 100);
  }
}
