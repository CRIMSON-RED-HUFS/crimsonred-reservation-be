package com.crimsonred.reservation.announcement.infrastructure.persistence;

import com.crimsonred.reservation.announcement.application.port.AnnouncementSender;
import com.crimsonred.reservation.announcement.application.port.DeliveryRepository;
import com.crimsonred.reservation.announcement.domain.AnnouncementDelivery;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class JpaDeliveryRepository implements DeliveryRepository {
  private final JpaDeliveryQueries queries;
  private final JdbcTemplate jdbc;

  public JpaDeliveryRepository(JpaDeliveryQueries queries, JdbcTemplate jdbc) {
    this.queries = queries;
    this.jdbc = jdbc;
  }

  public List<AnnouncementDelivery> findDue(Instant now) {
    return queries.findDue(now, PageRequest.of(0, 50));
  }

  public void enqueue(LocalDate date, Instant now) {
    jdbc.update(
        "insert ignore into announcement_delivery(notice_date,status,attempts,next_attempt_at)"
            + " values(?,'PENDING',0,?)",
        date,
        java.sql.Timestamp.from(now));
  }

  public void recover(Instant before) {
    jdbc.update(
        "update announcement_delivery set status='UNKNOWN',"
            + "error_summary='발송 중 서버 중단: 채널 확인이 필요합니다.' "
            + "where status='SENDING' and started_at < ?",
        java.sql.Timestamp.from(before));
  }

  public boolean claim(Long id, Instant now) {
    return jdbc.update(
            "update announcement_delivery set status='SENDING',attempts=attempts+1,started_at=?"
                + " where id=? and status in ('PENDING','FAILED') and attempts<3 and"
                + " next_attempt_at<=?",
            java.sql.Timestamp.from(now),
            id,
            java.sql.Timestamp.from(now))
        == 1;
  }

  public int attempts(Long id) {
    return jdbc.queryForObject(
        "select attempts from announcement_delivery where id=?", Integer.class, id);
  }

  public void complete(
      Long id, AnnouncementSender.Result result, int attempts, Instant nextAttemptAt) {
    jdbc.update(
        "update announcement_delivery set"
            + " status=?,attempts=?,next_attempt_at=?,message_id=?,error_summary=? where id=? and"
            + " status='SENDING'",
        result.status(),
        attempts,
        java.sql.Timestamp.from(nextAttemptAt),
        result.messageId(),
        result.error(),
        id);
  }
}

interface JpaDeliveryQueries extends JpaRepository<AnnouncementDelivery, Long> {
  @Query("select d from AnnouncementDelivery d where d.status in ('PENDING','FAILED')"
      + " and d.attempts<3 and d.nextAttemptAt<=:now order by d.id")
  List<AnnouncementDelivery> findDue(@Param("now") Instant now, Pageable page);
}
