package com.crimsonred.reservation.shared.application.port;

public interface TransactionRunner {
  <T> T required(java.util.function.Supplier<T> action);

  default void required(Runnable action) {
    required(
        () -> {
          action.run();
          return null;
        });
  }
}
