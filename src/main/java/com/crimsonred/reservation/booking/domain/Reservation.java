package com.crimsonred.reservation.booking.domain;

import com.crimsonred.reservation.shared.domain.BusinessException;
import com.crimsonred.reservation.shared.domain.InputText;
import java.time.Instant;

public class Reservation {
  public static final int NAME_MAX_LENGTH = 20;
  private Long id;
  private Long memberId;
  private Long weekId;
  private Instant startAt;
  private Instant endAt;
  private String rehearsalName;
  private ReservationType reservationType;
  private String status;
  private String requestKey;
  private Instant createdAt;
  private Instant cancelledAt;
  private Long cancelledBy;
  private String cancelReason;

  public Long getId() {
    return id;
  }

  public Long getMemberId() {
    return memberId;
  }

  public Long getWeekId() {
    return weekId;
  }

  public Instant getStartAt() {
    return startAt;
  }

  public Instant getEndAt() {
    return endAt;
  }

  public String getRehearsalName() {
    return rehearsalName;
  }

  public ReservationType getReservationType() {
    return reservationType;
  }

  public String getStatus() {
    return status;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public Instant getCancelledAt() {
    return cancelledAt;
  }

  public String getCancelReason() {
    return cancelReason;
  }

  protected Reservation() {}

  public static Reservation create(
      Long memberId,
      Long weekId,
      Instant startAt,
      Instant endAt,
      String rehearsalName,
      ReservationType reservationType,
      String requestKey,
      Instant now) {
    if (reservationType == null) throw BusinessException.bad("예약 유형을 선택해주세요.");
    var reservation = new Reservation();
    reservation.reservationType = reservationType;
    reservation.memberId = memberId;
    reservation.weekId = weekId;
    reservation.startAt = startAt;
    reservation.endAt = endAt;
    reservation.rehearsalName = InputText.singleLine(rehearsalName, NAME_MAX_LENGTH, "예약 이름");
    reservation.requestKey = requestKey;
    reservation.status = "ACTIVE";
    reservation.createdAt = now;
    return reservation;
  }

  public void cancel(Long actor, Instant now) {
    if (!memberId.equals(actor)) throw BusinessException.missing();
    if (!status.equals("ACTIVE"))
      throw BusinessException.conflict("ALREADY_CANCELLED", "이미 취소된 예약입니다.");
    if (!now.isBefore(startAt))
      throw BusinessException.conflict("ALREADY_STARTED", "이용 시작 전까지만 취소할 수 있습니다.");
    status = "CANCELLED";
    cancelledAt = now;
    cancelledBy = actor;
    cancelReason = null;
  }
}
