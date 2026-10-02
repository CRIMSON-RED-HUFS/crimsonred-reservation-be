package com.crimsonred.reservation.shared.application;

public class StorageConflict extends RuntimeException {
  public StorageConflict(Throwable cause) {
    super("Conflicting persisted state", cause);
  }
}
