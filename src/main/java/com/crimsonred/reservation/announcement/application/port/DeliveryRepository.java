package com.crimsonred.reservation.announcement.application.port;

import com.crimsonred.reservation.announcement.domain.AnnouncementDelivery;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public interface DeliveryRepository {
  List<AnnouncementDelivery> findDue(Instant now);

  void enqueue(LocalDate date, Instant now);

  void recover(Instant before);

  boolean claim(Long id, Instant now);

  int attempts(Long id);

  void complete(Long id, AnnouncementSender.Result result, int attempts, Instant nextAttemptAt);
}
