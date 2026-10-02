package com.crimsonred.reservation.booking.presentation;

import static com.crimsonred.reservation.auth.presentation.CurrentMember.id;

import com.crimsonred.reservation.booking.application.BookingService;
import com.crimsonred.reservation.booking.application.BookingService.*;
import com.crimsonred.reservation.booking.application.ReservationView;
import com.crimsonred.reservation.booking.domain.ReservationType;
import com.crimsonred.reservation.booking.domain.Reservation;
import com.crimsonred.reservation.shared.domain.InputText;
import com.crimsonred.reservation.shared.presentation.ApiPages;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.net.URI;
import java.time.*;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
public class BookingController {
  private final BookingService bookings;

  public BookingController(BookingService bookings) {
    this.bookings = bookings;
  }

  public record Reserve(
      @NotNull LocalDate weekStart,
      @NotNull Instant startAt,
      @NotNull Instant endAt,
      @NotBlank @Size(max = Reservation.NAME_MAX_LENGTH, message = "예약 이름은 최대 20자입니다.")
      @Pattern(regexp = InputText.SINGLE_LINE_PATTERN, message = "예약 이름에 제어문자나 보이지 않는 문자를 사용할 수 없습니다.") String rehearsalName,
      @NotNull ReservationType reservationType) {}

  @GetMapping("/weeks")
  List<WeekView> weeks(
      Authentication auth, @RequestParam LocalDate from, @RequestParam LocalDate to) {
    return bookings.weeks(id(auth), from, to);
  }

  @GetMapping("/weeks/booking/timetable")
  Timetable bookingTimetable(Authentication auth) {
    return bookings.bookingTimetable(id(auth));
  }

  @GetMapping("/weeks/{weekStart}/timetable")
  Timetable timetable(Authentication auth, @PathVariable LocalDate weekStart) {
    return bookings.timetable(id(auth), weekStart);
  }

  @PostMapping("/reservations")
  ResponseEntity<ReservationView> reserve(
      Authentication auth,
      @RequestHeader(value = "Idempotency-Key", required = false) String key,
      @Valid @RequestBody Reserve input) {
    var result = bookings.reserve(
        id(auth),
        key,
        new BookingInput(input.weekStart(), input.startAt(), input.endAt(), input.rehearsalName(),
            input.reservationType()));
    var location = URI.create("/api/v1/reservations/" + result.reservation().id());
    return result.created()
        ? ResponseEntity.created(location).body(result.reservation())
        : ResponseEntity.ok().location(location).body(result.reservation());
  }

  @GetMapping("/reservations/{reservationId}")
  ReservationView get(Authentication auth, @PathVariable Long reservationId) {
    return bookings.get(id(auth), reservationId);
  }

  @GetMapping("/members/me/reservations")
  ApiPages.Response<ReservationView> mine(
      Authentication auth,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size,
      @RequestParam(defaultValue = "all") String scope) {
    return ApiPages.response(bookings.mine(id(auth), page, size, scope));
  }

  @PostMapping("/reservations/{reservationId}/cancel")
  ReservationView cancel(Authentication auth, @PathVariable Long reservationId) {
    return bookings.cancel(id(auth), reservationId);
  }

}
