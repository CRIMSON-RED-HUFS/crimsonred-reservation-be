package com.crimsonred.reservation.booking.infrastructure.persistence;

import com.crimsonred.reservation.booking.application.port.WeekRepository;
import com.crimsonred.reservation.booking.domain.BookingWeek;
import jakarta.persistence.LockModeType;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public class JpaWeekRepository implements WeekRepository {
  private final JpaWeekQueries queries;

  public JpaWeekRepository(JpaWeekQueries queries) {
    this.queries = queries;
  }

  public Optional<BookingWeek> findByWeekStart(LocalDate date) {
    return queries.findByWeekStart(date);
  }

  public Optional<BookingWeek> locked(LocalDate date) {
    return queries.locked(date);
  }

  public Optional<BookingWeek> lockedId(Long id) {
    return queries.lockedId(id);
  }

  public List<BookingWeek> findByWeekStartBetweenOrderByWeekStart(LocalDate from, LocalDate to) {
    return queries.findByWeekStartBetweenOrderByWeekStart(from, to);
  }

  public BookingWeek saveAndFlush(BookingWeek week) {
    return queries.saveAndFlush(week);
  }
}

interface JpaWeekQueries extends JpaRepository<BookingWeek, Long> {
  Optional<BookingWeek> findByWeekStart(LocalDate date);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select w from BookingWeek w where w.weekStart=:date")
  Optional<BookingWeek> locked(@Param("date") LocalDate date);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select w from BookingWeek w where w.id=:id")
  Optional<BookingWeek> lockedId(@Param("id") Long id);

  List<BookingWeek> findByWeekStartBetweenOrderByWeekStart(LocalDate from, LocalDate to);
}
