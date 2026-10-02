package com.crimsonred.reservation.announcement.infrastructure.scheduling;

import com.crimsonred.reservation.announcement.application.AnnouncementService;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Component
public class AnnouncementScheduler {
  private static final Logger log = LoggerFactory.getLogger(AnnouncementScheduler.class);
  private final AnnouncementService announcements;

  public AnnouncementScheduler(AnnouncementService announcements) {
    this.announcements = announcements;
  }

  @EventListener(ApplicationReadyEvent.class)
  @Scheduled(cron = "${app.announcement-cron}", zone = "Asia/Seoul")
  @Scheduled(fixedDelay = 30000, initialDelay = 30000)
  public void recover() {
    try {
      announcements.recoverAndPump();
    } catch (RuntimeException e) {
      log.warn("Announcement recovery failed; retry on next schedule. exception={}", e.getClass().getSimpleName());
    }
  }
}
