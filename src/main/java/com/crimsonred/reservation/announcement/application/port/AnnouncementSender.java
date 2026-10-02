package com.crimsonred.reservation.announcement.application.port;

public interface AnnouncementSender {
  record Result(String status, String messageId, int retrySeconds, String error) {}

  Result send(String content, String schedule);
}
