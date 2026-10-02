package com.crimsonred.reservation.booking.application.port;

import com.crimsonred.reservation.booking.domain.Reservation;
import com.crimsonred.reservation.shared.application.PageQuery;
import com.crimsonred.reservation.shared.application.PageResult;
import java.time.Instant;
import java.util.Optional;

public interface ReservationRepository {
  Optional<Reservation> findById(Long id);

  Optional<Reservation> findByMemberIdAndRequestKey(Long memberId, String key);

  Reservation save(Reservation reservation);

  Reservation saveAndFlush(Reservation reservation);

  PageResult<Reservation> findByMemberId(Long memberId, PageQuery page);

  PageResult<Reservation> upcoming(Long memberId, Instant now, PageQuery page);

  PageResult<Reservation> history(Long memberId, Instant now, PageQuery page);
}
