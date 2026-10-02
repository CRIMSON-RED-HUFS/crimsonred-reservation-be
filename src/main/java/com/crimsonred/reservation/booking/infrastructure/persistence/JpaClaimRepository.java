package com.crimsonred.reservation.booking.infrastructure.persistence;

import com.crimsonred.reservation.booking.application.port.ClaimRepository;
import com.crimsonred.reservation.booking.domain.ActiveSlotClaim;
import jakarta.persistence.EntityManager;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public class JpaClaimRepository implements ClaimRepository {
  private final JpaClaimQueries queries;
  private final EntityManager em;

  public JpaClaimRepository(JpaClaimQueries queries, EntityManager em) {
    this.queries = queries;
    this.em = em;
  }

  public boolean existsBySlotIdIn(Collection<Long> ids) {
    return queries.existsBySlotIdIn(ids);
  }

  public int deleteByReservationId(Long id) {
    return queries.deleteByReservationId(id);
  }

  public void create(List<ActiveSlotClaim> claims) {
    claims.forEach(em::persist);
    em.flush();
  }
}

interface JpaClaimQueries extends JpaRepository<ActiveSlotClaim, Long> {
  boolean existsBySlotIdIn(Collection<Long> ids);

  @Modifying
  @Query("delete from ActiveSlotClaim c where c.reservationId=:id")
  int deleteByReservationId(@Param("id") Long reservationId);
}
