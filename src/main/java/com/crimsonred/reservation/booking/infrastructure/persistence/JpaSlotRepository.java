package com.crimsonred.reservation.booking.infrastructure.persistence;

import com.crimsonred.reservation.booking.application.TimetableRow;
import com.crimsonred.reservation.booking.application.port.SlotRepository;
import com.crimsonred.reservation.booking.domain.BookingSlot;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public class JpaSlotRepository implements SlotRepository {
  private final JpaSlotQueries queries;
  private final EntityManager em;

  public JpaSlotRepository(JpaSlotQueries queries, EntityManager em) {
    this.queries = queries;
    this.em = em;
  }

  public List<BookingSlot> findByWeekIdOrderByStartAt(Long id) {
    return queries.findByWeekIdOrderByStartAt(id);
  }

  public List<BookingSlot> findInRange(Long weekId, Instant start, Instant end) {
    return queries.findByWeekIdAndStartAtGreaterThanEqualAndStartAtLessThanOrderByStartAt(
        weekId, start, end);
  }

  public void saveAll(List<BookingSlot> slots) {
    queries.saveAll(slots);
  }

  public List<TimetableRow> rows(Long weekId) {
    return em.createQuery(
            """
            select new com.crimsonred.reservation.booking.application.TimetableRow(
              s.id,s.startAt,s.endAt,s.blocked,s.blockReason,r.id,r.memberId,m.name,r.rehearsalName,r.reservationType)
            from BookingSlot s left join ActiveSlotClaim c on c.slotId=s.id
            left join Reservation r on r.id=c.reservationId left join Member m on m.id=r.memberId
            where s.weekId=:week order by s.startAt
            """,
            TimetableRow.class)
        .setParameter("week", weekId)
        .getResultList();
  }
}

interface JpaSlotQueries extends JpaRepository<BookingSlot, Long> {
  List<BookingSlot> findByWeekIdOrderByStartAt(Long weekId);

  List<BookingSlot> findByWeekIdAndStartAtGreaterThanEqualAndStartAtLessThanOrderByStartAt(
      Long weekId, Instant start, Instant end);
}
