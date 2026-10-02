package com.crimsonred.reservation.booking.application;

import com.crimsonred.reservation.booking.application.port.ClaimRepository;
import com.crimsonred.reservation.booking.application.port.ReservationRepository;
import com.crimsonred.reservation.booking.application.port.SlotRepository;
import com.crimsonred.reservation.booking.application.port.WeekRepository;
import com.crimsonred.reservation.booking.domain.ActiveSlotClaim;
import com.crimsonred.reservation.booking.domain.BookingRules;
import com.crimsonred.reservation.booking.domain.BookingSlot;
import com.crimsonred.reservation.booking.domain.BookingWeek;
import com.crimsonred.reservation.booking.domain.Reservation;
import com.crimsonred.reservation.booking.domain.ReservationType;
import com.crimsonred.reservation.booking.domain.WeeklySchedule;
import com.crimsonred.reservation.member.application.MemberService;
import com.crimsonred.reservation.shared.application.PageQuery;
import com.crimsonred.reservation.shared.application.PageResult;
import com.crimsonred.reservation.shared.application.StorageConflict;
import com.crimsonred.reservation.shared.application.port.TransactionRunner;
import com.crimsonred.reservation.shared.domain.BusinessException;
import com.crimsonred.reservation.shared.domain.InputText;
import java.time.*;
import java.util.*;

public class BookingService {
  private final WeekRepository weeks;
  private final SlotRepository slots;
  private final ReservationRepository reservations;
  private final ClaimRepository claims;
  private final MemberService members;
  private final TransactionRunner tx;
  private final Clock clock;
  private final WeeklySchedule schedule;

  public BookingService(
      WeekRepository weeks,
      SlotRepository slots,
      ReservationRepository reservations,
      ClaimRepository claims,
      MemberService members,
      TransactionRunner tx,
      Clock clock,
      WeeklySchedule schedule) {
    this.weeks = weeks;
    this.slots = slots;
    this.reservations = reservations;
    this.claims = claims;
    this.members = members;
    this.tx = tx;
    this.clock = clock;
    this.schedule = schedule;
  }

  public record BookingInput(
      LocalDate weekStart, Instant startAt, Instant endAt, String rehearsalName,
      ReservationType reservationType) {
    public BookingInput {
      rehearsalName = InputText.singleLine(rehearsalName, Reservation.NAME_MAX_LENGTH, "예약 이름");
    }
  }

  public record WeekView(
      Long id, LocalDate weekStart, boolean published, Instant opensAt, Instant closesAt) {}

  public record SlotView(
      Long id,
      Instant startAt,
      Instant endAt,
      String status,
      String blockReason,
      Long reservationId,
      String memberName,
      String rehearsalName,
      ReservationType reservationType,
      boolean mine) {}

  public record Timetable(
      LocalDate weekStart,
      LocalDate bookingWeekStart,
      boolean published,
      Instant opensAt,
      Instant closesAt,
      Instant serverTime,
      List<SlotView> slots) {}

  public record ReservationResult(ReservationView reservation, boolean created) {}

  private ReservationView view(Reservation r) {
    return new ReservationView(
        r.getId(),
        r.getMemberId(),
        r.getWeekId(),
        r.getStartAt(),
        r.getEndAt(),
        r.getRehearsalName(),
        r.getReservationType(),
        r.getStatus(),
        r.getCreatedAt(),
        r.getCancelledAt(),
        r.getCancelReason(),
        null);
  }

  private WeekView view(BookingWeek w) {
    return new WeekView(
        w.getId(), w.getWeekStart(), w.getPublished(), w.getOpensAt(), w.getClosesAt());
  }

  public ReservationResult reserve(Long actor, String key, BookingInput input) {
    members.approved(actor);
    if (input.reservationType() == null) throw BusinessException.bad("예약 유형을 선택해주세요.");
    if (key == null || !key.matches("[A-Za-z0-9_-]{1,64}"))
      throw BusinessException.bad("Idempotency-Key가 필요합니다.");
    BookingRules.range(input.weekStart(), input.startAt(), input.endAt());
    ensureBookingWeek();
    try {
      return tx.required(
          () -> {
            // ponytail: week writes serialize; use ordered slot locks if measured contention
            // requires it.
            var w = weeks.locked(input.weekStart()).orElseThrow(BusinessException::missing);
            members.approvedForUpdate(actor);
            var existing = reservations.findByMemberIdAndRequestKey(actor, key);
            if (existing.isPresent())
              return new ReservationResult(same(existing.get(), input, w.getId()), false);
            Instant now = clock.instant();
            BookingRules.open(w, now, input.startAt());
            var selected =
                slots.findInRange(w.getId(), input.startAt(), input.endAt());
            long count = Duration.between(input.startAt(), input.endAt()).toMinutes() / 30;
            if (selected.size() != count || selected.stream().anyMatch(s -> s.getBlocked()))
              throw BusinessException.conflict("SLOT_UNAVAILABLE", "예약할 수 없는 시간이 포함되어 있습니다.");
            if (claims.existsBySlotIdIn(selected.stream().map(s -> s.getId()).toList()))
              throw BusinessException.conflict("SLOT_TAKEN", "다른 부원이 먼저 예약했습니다.");
            var r =
                Reservation.create(
                    actor,
                    w.getId(),
                    input.startAt(),
                    input.endAt(),
                    input.rehearsalName(),
                    input.reservationType(),
                    key,
                    now);
            reservations.saveAndFlush(r);
            claims.create(
                selected.stream().map(s -> new ActiveSlotClaim(s.getId(), r.getId())).toList());
            return new ReservationResult(view(r), true);
          });
    } catch (StorageConflict e) {
      // An identical key racing across different week locks still resolves through the DB unique
      // constraint.
      var r = reservations.findByMemberIdAndRequestKey(actor, key);
      var w = weeks.findByWeekStart(input.weekStart()).orElseThrow(BusinessException::missing);
      if (r.isPresent()) return new ReservationResult(same(r.get(), input, w.getId()), false);
      throw BusinessException.conflict("SLOT_TAKEN", "다른 부원이 먼저 예약했습니다.");
    }
  }

  private ReservationView same(Reservation r, BookingInput input, Long weekId) {
    if (r.getReservationType() != input.reservationType()
        || !r.getWeekId().equals(weekId)
        || !r.getStartAt().equals(input.startAt())
        || !r.getEndAt().equals(input.endAt())
        || !r.getRehearsalName().equals(input.rehearsalName()))
      throw BusinessException.conflict("IDEMPOTENCY_MISMATCH", "같은 요청 키로 다른 예약을 신청할 수 없습니다.");
    return view(r);
  }

  public ReservationView cancel(Long actor, Long id) {
    var snapshot = reservations.findById(id).orElseThrow(BusinessException::missing);
    return tx.required(
        () -> {
          weeks.lockedId(snapshot.getWeekId()).orElseThrow(BusinessException::missing);
          members.current(actor);
          var r = reservations.findById(id).orElseThrow(BusinessException::missing);
          r.cancel(actor, clock.instant());
          claims.deleteByReservationId(r.getId());
          reservations.save(r);
          return view(r);
        });
  }

  public ReservationView get(Long actor, Long id) {
    members.current(actor);
    var r = reservations.findById(id).orElseThrow(BusinessException::missing);
    if (!r.getMemberId().equals(actor)) throw BusinessException.missing();
    return view(r);
  }

  public PageResult<ReservationView> mine(Long actor, int page, int size, String scope) {
    members.current(actor);
    var results =
        switch (scope) {
          case "upcoming" -> reservations.upcoming(actor, clock.instant(), page(page, size));
          case "history" -> reservations.history(actor, clock.instant(), page(page, size));
          case "all" -> reservations.findByMemberId(actor, page(page, size));
          default -> throw BusinessException.bad("잘못된 예약 조회 범위입니다.");
        };
    return results.map(this::view);
  }

  private PageQuery page(int page, int size) {
    return new PageQuery(page, size, "startAt");
  }

  public List<WeekView> weeks(Long actor, LocalDate from, LocalDate to) {
    members.current(actor);
    if (to.isBefore(from) || from.plusMonths(6).isBefore(to))
      throw BusinessException.bad("조회 기간은 최대 6개월입니다.");
    var bookingWeek = bookingWeek();
    var displayed = new TreeMap<LocalDate, WeekView>();
    for (var w : weeks.findByWeekStartBetweenOrderByWeekStart(from, to)) {
      if (w.getWeekStart().isAfter(bookingWeek)) continue;
      displayed.put(w.getWeekStart(), w.getWeekStart().equals(bookingWeek) && !w.getAutomatic()
          ? scheduledWeek(bookingWeek, w.getId()) : view(w));
    }
    if (!bookingWeek.isBefore(from) && !bookingWeek.isAfter(to))
      displayed.putIfAbsent(bookingWeek, scheduledWeek(bookingWeek, null));
    return List.copyOf(displayed.values());
  }

  public Timetable timetable(Long actor, LocalDate date) {
    return timetable(actor, date, clock.instant());
  }

  private Timetable timetable(Long actor, LocalDate date, Instant now) {
    var m = members.current(actor);
    BookingRules.monday(date);
    var bookingWeek = WeeklySchedule.bookingWeek(now);
    if (date.isAfter(bookingWeek))
      return new Timetable(date, bookingWeek, false, null, null, now, List.of());
    var w = weeks.findByWeekStart(date).orElse(null);
    boolean preview = date.equals(bookingWeek) && (w == null || !w.getAutomatic());
    var week = preview ? scheduledWeek(date, w == null ? null : w.getId())
        : w == null ? null : view(w);
    boolean detailed = m.approved();
    boolean open =
        date.equals(bookingWeek) && week != null && week.published()
            && !now.isBefore(week.opensAt()) && now.isBefore(week.closesAt());
    var displayed = new TreeMap<Instant, TimetableRow>();
    var starts = schedule.starts(date);
    if (w != null) {
      for (var row : slots.rows(w.getId())) {
        if (preview) {
          boolean enabled = starts.contains(row.startAt());
          row = new TimetableRow(row.id(), row.startAt(), row.endAt(), !enabled,
              enabled ? null : "운영 시간 외", row.reservationId(), row.memberId(),
              row.memberName(), row.rehearsalName(), row.reservationType());
        }
        if (!"운영 시간 외".equals(row.blockReason()) || row.reservationId() != null)
          displayed.put(row.startAt(), row);
      }
    }
    if (preview || date.isBefore(bookingWeek)) {
      // Reads preview the fixed schedule; only booking commands persist or reconcile it.
      for (var start : starts)
        displayed.putIfAbsent(start, new TimetableRow(null, start, start.plusSeconds(1800),
            !preview, null, null, null, null, null, null));
    }
    var views =
        displayed.values().stream()
            .map(
                s ->
                    new SlotView(
                        s.id(),
                        s.startAt(),
                        s.endAt(),
                        s.reservationId() != null
                            ? "BOOKED"
                            : (s.blocked() || !open || !now.isBefore(s.startAt())
                                ? "UNAVAILABLE"
                                : "AVAILABLE"),
                        s.blockReason(),
                        s.reservationId(),
                        detailed ? s.memberName() : null,
                        detailed ? s.rehearsalName() : null,
                        s.reservationType(),
                        actor.equals(s.memberId())))
            .toList();
    return new Timetable(date, bookingWeek, week != null && week.published(),
        week == null ? null : week.opensAt(), week == null ? null : week.closesAt(), now, views);
  }

  private WeekView scheduledWeek(LocalDate date, Long id) {
    return new WeekView(id, date, true, WeeklySchedule.opensAt(date), WeeklySchedule.closesAt(date));
  }

  public LocalDate bookingWeek() {
    return WeeklySchedule.bookingWeek(clock.instant());
  }

  public Timetable bookingTimetable(Long actor) {
    Instant now = clock.instant();
    return timetable(actor, WeeklySchedule.bookingWeek(now), now);
  }

  /** Provision on first use; the unique week date resolves concurrent first requests. */
  public BookingWeek ensureBookingWeek() {
    return ensureWeek(bookingWeek());
  }

  private BookingWeek ensureWeek(LocalDate date) {
    var existing = weeks.findByWeekStart(date);
    if (existing.isPresent() && existing.get().getAutomatic()) return existing.get();
    try {
      return tx.required(
          () -> {
            var w = weeks.locked(date).orElseGet(() ->
                BookingWeek.create(date, WeeklySchedule.opensAt(date),
                    WeeklySchedule.closesAt(date), true));
            if (w.getAutomatic()) return w;
            w.useWeeklySchedule();
            weeks.saveAndFlush(w);
            var wanted = schedule.starts(date);
            var previous = slots.findByWeekIdOrderByStartAt(w.getId());
            // Keep existing IDs and claims while replacing manually configured hours.
            for (var slot : previous) {
              boolean enabled = wanted.remove(slot.getStartAt());
              slot.changeBlocked(!enabled, enabled ? null : "운영 시간 외");
            }
            slots.saveAll(previous);
            slots.saveAll(wanted.stream().map(start -> BookingSlot.create(w.getId(), start)).toList());
            return w;
          });
    } catch (StorageConflict error) {
      // The winning transaction commits the week and all its slots together.
      return weeks.findByWeekStart(date).filter(BookingWeek::getAutomatic).orElseThrow(() -> error);
    }
  }
}
