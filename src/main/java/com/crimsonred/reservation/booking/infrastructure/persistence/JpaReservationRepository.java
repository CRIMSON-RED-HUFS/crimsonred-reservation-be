package com.crimsonred.reservation.booking.infrastructure.persistence;

import com.crimsonred.reservation.booking.application.port.ReservationRepository;
import com.crimsonred.reservation.booking.domain.Reservation;
import com.crimsonred.reservation.shared.application.PageQuery;
import com.crimsonred.reservation.shared.application.PageResult;
import com.crimsonred.reservation.shared.infrastructure.SpringPages;
import java.time.Instant;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public class JpaReservationRepository implements ReservationRepository {
  private final JpaReservationQueries queries;

  public JpaReservationRepository(JpaReservationQueries queries) {
    this.queries = queries;
  }

  public Optional<Reservation> findById(Long id) {
    return queries.findById(id);
  }

  public Optional<Reservation> findByMemberIdAndRequestKey(Long id, String key) {
    return queries.findByMemberIdAndRequestKey(id, key);
  }

  public Reservation save(Reservation reservation) {
    return queries.save(reservation);
  }

  public Reservation saveAndFlush(Reservation reservation) {
    return queries.saveAndFlush(reservation);
  }

  public PageResult<Reservation> findByMemberId(Long id, PageQuery page) {
    return SpringPages.result(queries.findByMemberId(id, SpringPages.request(page)), page);
  }

  public PageResult<Reservation> upcoming(Long id, Instant now, PageQuery page) {
    return SpringPages.result(queries.upcoming(id, now, SpringPages.request(page)), page);
  }

  public PageResult<Reservation> history(Long id, Instant now, PageQuery page) {
    return SpringPages.result(queries.history(id, now, SpringPages.request(page)), page);
  }
}

interface JpaReservationQueries extends JpaRepository<Reservation, Long> {
  Optional<Reservation> findByMemberIdAndRequestKey(Long memberId, String key);

  Page<Reservation> findByMemberId(Long memberId, Pageable pageable);

  @Query("select r from Reservation r where r.memberId=:id and r.status='ACTIVE' and r.endAt>:now")
  Page<Reservation> upcoming(@Param("id") Long id, @Param("now") Instant now, Pageable pageable);

  @Query(
      "select r from Reservation r where r.memberId=:id and (r.status='CANCELLED' or"
          + " r.endAt<=:now)")
  Page<Reservation> history(@Param("id") Long id, @Param("now") Instant now, Pageable pageable);

}
