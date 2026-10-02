package com.crimsonred.reservation.announcement.application;

import com.crimsonred.reservation.announcement.application.port.AnnouncementSender;
import com.crimsonred.reservation.announcement.application.port.DeliveryRepository;
import com.crimsonred.reservation.booking.application.TimetableRow;
import com.crimsonred.reservation.booking.application.port.SlotRepository;
import com.crimsonred.reservation.booking.application.port.WeekRepository;
import com.crimsonred.reservation.booking.domain.BookingRules;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAdjusters;
import java.util.*;
import java.util.stream.Collectors;

public class AnnouncementService {
  private final DeliveryRepository deliveries;
  private final WeekRepository weeks;
  private final SlotRepository slots;
  private final AnnouncementSender webhook;
  private final Clock clock;
  private final boolean enabled;
  private final String baseUrl;
  private final java.util.function.UnaryOperator<ZonedDateTime> nextNotice;

  public AnnouncementService(
      DeliveryRepository deliveries,
      WeekRepository weeks,
      SlotRepository slots,
      AnnouncementSender webhook,
      Clock clock,
      boolean enabled,
      String baseUrl,
      java.util.function.UnaryOperator<ZonedDateTime> nextNotice) {
    this.deliveries = deliveries;
    this.weeks = weeks;
    this.slots = slots;
    this.webhook = webhook;
    this.clock = clock;
    this.enabled = enabled;
    this.baseUrl = baseUrl.replaceAll("/+$", "");
    this.nextNotice = nextNotice;
  }

  public void recoverAndPump() {
    if (!enabled) return;
    var now = clock.instant().atZone(BookingRules.SEOUL);
    var sunday = now.toLocalDate().with(TemporalAdjusters.previousOrSame(DayOfWeek.SUNDAY));
    var due = nextNotice.apply(sunday.atStartOfDay(BookingRules.SEOUL).minusSeconds(1));
    if (due != null && due.isAfter(now))
      due = nextNotice.apply(sunday.minusWeeks(1).atStartOfDay(BookingRules.SEOUL).minusSeconds(1));
    if (due != null && !due.isAfter(now)) enqueue(due.toLocalDate());
    deliveries.recover(clock.instant().minusSeconds(60));
    pump();
  }

  public void enqueue(LocalDate sunday) {
    deliveries.enqueue(sunday, clock.instant());
  }

  public void pump() {
    var due = deliveries.findDue(clock.instant());
    for (var item : due) {
      if (!deliveries.claim(item.getId(), clock.instant())) continue;
      var result = send(item.getNoticeDate());
      int attempts = deliveries.attempts(item.getId());
      int delay =
          result.retrySeconds() == 0
              ? 0
              : Math.max(result.retrySeconds(), 30 * (1 << (attempts - 1)));
      deliveries.complete(
          item.getId(),
          result,
          result.status().equals("FAILED") && result.retrySeconds() == 0 ? 3 : attempts,
          clock.instant().plusSeconds(delay));
    }
  }

  private AnnouncementSender.Result send(LocalDate noticeDate) {
    Message message;
    try {
      message = message(noticeDate);
    } catch (Exception e) {
      return new AnnouncementSender.Result("FAILED", null, 30, "예약 현황 조회 실패");
    }
    try {
      return webhook.send(message.content(), message.schedule());
    } catch (Exception e) {
      // A sender may have delivered before throwing; retrying could duplicate the notice.
      return new AnnouncementSender.Result("UNKNOWN", null, 0, "전송 결과 불명: 채널 확인이 필요합니다.");
    }
  }

  public record Message(String content, String schedule) {}

  public Message message(LocalDate sunday) {
    LocalDate next = sunday.with(TemporalAdjusters.next(DayOfWeek.MONDAY));
    var w = weeks.findByWeekStart(next).filter(x -> x.getPublished());
    var rows = w.map(x -> slots.rows(x.getId())).orElse(List.of());
    var reservations = rows.stream().filter(row -> row.reservationId() != null)
        .collect(Collectors.groupingBy(TimetableRow::reservationId, LinkedHashMap::new, Collectors.toList()));
    var text = new StringBuilder("크림슨레드 이번 주 동아리방 | " + next + " ~ " + next.plusDays(6) + "\n\n");
    var summary = new StringBuilder("🎸 이번 주 동아리방 예약 현황\n" + next + " ~ " + next.plusDays(6) + "\n");
    for (int i = 0; i < 7; i++) {
      LocalDate date = next.plusDays(i);
      var daily =
          reservations.values().stream()
              .filter(covered -> covered.getFirst().startAt().atZone(BookingRules.SEOUL).toLocalDate().equals(date))
              .toList();
      String label = date.format(DateTimeFormatter.ofPattern("MM/dd (E)", Locale.KOREAN));
      summary.append(label).append(": ").append(daily.size()).append("건\n");
      text.append(label).append("\n");
      for (var covered : daily) {
        var first = covered.getFirst();
        var end = covered.getLast().endAt().atZone(BookingRules.SEOUL);
        String endText = end.toLocalDate().equals(date) ? end.toLocalTime().toString() : "24:00";
        text.append(first.startAt().atZone(BookingRules.SEOUL).toLocalTime())
            .append("–")
            .append(endText)
            .append(" | ")
            .append(singleLine(first.memberName()))
            .append(" | ")
            .append(first.reservationType().label())
            .append(" | ")
            .append(singleLine(first.rehearsalName()))
            .append("\n");
      }
      if (daily.isEmpty()) text.append("예약 없음\n");
      text.append("\n");
    }
    summary
        .append("\n상세 현황: ")
        .append(baseUrl)
        .append("/?week=")
        .append(next)
        .append("\n다음 이용 주차 예약은 일요일 23:00에 열립니다.");
    return new Message(summary.toString(), text.toString());
  }

  private String singleLine(String s) {
    return s == null ? "" : s.replace('\n', ' ').replace('\r', ' ');
  }

}
