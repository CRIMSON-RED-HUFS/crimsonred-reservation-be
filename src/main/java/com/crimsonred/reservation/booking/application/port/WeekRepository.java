package com.crimsonred.reservation.booking.application.port;

import com.crimsonred.reservation.booking.domain.BookingWeek;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface WeekRepository {
  Optional<BookingWeek> findByWeekStart(LocalDate date);

  Optional<BookingWeek> locked(LocalDate date);

  Optional<BookingWeek> lockedId(Long id);

  List<BookingWeek> findByWeekStartBetweenOrderByWeekStart(LocalDate from, LocalDate to);

  BookingWeek saveAndFlush(BookingWeek week);
}
