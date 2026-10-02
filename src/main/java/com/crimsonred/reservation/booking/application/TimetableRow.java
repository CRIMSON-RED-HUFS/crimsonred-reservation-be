package com.crimsonred.reservation.booking.application;

import java.time.Instant;
import com.crimsonred.reservation.booking.domain.ReservationType;

public record TimetableRow(
    Long id,
    Instant startAt,
    Instant endAt,
    boolean blocked,
    String blockReason,
    Long reservationId,
    Long memberId,
    String memberName,
    String rehearsalName,
    ReservationType reservationType) {}
