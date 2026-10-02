package com.crimsonred.reservation.booking.application.port;

import com.crimsonred.reservation.booking.domain.ActiveSlotClaim;
import java.util.Collection;
import java.util.List;

public interface ClaimRepository {
  boolean existsBySlotIdIn(Collection<Long> ids);

  int deleteByReservationId(Long reservationId);

  void create(List<ActiveSlotClaim> claims);
}
