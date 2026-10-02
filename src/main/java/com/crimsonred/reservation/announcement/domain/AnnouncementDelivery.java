package com.crimsonred.reservation.announcement.domain;

import java.time.*;

public class AnnouncementDelivery {
  private Long id;
  private LocalDate noticeDate;
  private String status;
  private int attempts;
  private Instant nextAttemptAt;
  private Instant startedAt;
  private String messageId;
  private String errorSummary;

  public Long getId() {
    return id;
  }

  public LocalDate getNoticeDate() {
    return noticeDate;
  }

  public String getStatus() {
    return status;
  }

}
