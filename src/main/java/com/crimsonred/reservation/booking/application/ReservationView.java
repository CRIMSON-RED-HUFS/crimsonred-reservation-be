package com.crimsonred.reservation.booking.application;

import java.time.Instant;
import com.crimsonred.reservation.booking.domain.ReservationType;

public record ReservationView(
    Long id,
    Long memberId,
    Long weekId,
    Instant startAt,
    Instant endAt,
    String rehearsalName,
    ReservationType reservationType,
    String status,
    Instant createdAt,
    Instant cancelledAt,
    String cancelReason,
    String memberName) {}
