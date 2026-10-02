package com.crimsonred.reservation.booking.application.port;

import com.crimsonred.reservation.booking.application.TimetableRow;
import com.crimsonred.reservation.booking.domain.BookingSlot;
import java.time.Instant;
import java.util.List;

public interface SlotRepository {
  List<BookingSlot> findByWeekIdOrderByStartAt(Long weekId);

  /** Slots starting in [start, end), ordered by start time. */
  List<BookingSlot> findInRange(Long weekId, Instant start, Instant end);

  void saveAll(List<BookingSlot> slots);

  List<TimetableRow> rows(Long weekId);
}
