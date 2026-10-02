package com.crimsonred.reservation.configuration;

import com.crimsonred.reservation.announcement.application.AnnouncementService;
import com.crimsonred.reservation.announcement.application.port.AnnouncementSender;
import com.crimsonred.reservation.announcement.application.port.DeliveryRepository;
import com.crimsonred.reservation.auth.application.AuthService;
import com.crimsonred.reservation.auth.application.port.PasswordHasher;
import com.crimsonred.reservation.auth.application.port.VerificationRepository;
import com.crimsonred.reservation.booking.application.BookingService;
import com.crimsonred.reservation.booking.application.port.ClaimRepository;
import com.crimsonred.reservation.booking.application.port.ReservationRepository;
import com.crimsonred.reservation.booking.application.port.SlotRepository;
import com.crimsonred.reservation.booking.application.port.WeekRepository;
import com.crimsonred.reservation.booking.domain.WeeklySchedule;
import com.crimsonred.reservation.member.application.MemberService;
import com.crimsonred.reservation.member.application.port.MemberRepository;
import com.crimsonred.reservation.shared.application.port.TransactionRunner;
import java.time.Clock;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.*;
import org.springframework.scheduling.support.CronExpression;

@Configuration
public class ApplicationConfig {
  @Bean
  Clock clock() {
    return Clock.systemUTC();
  }

  @Bean
  AuthService authService(
      MemberRepository members,
      VerificationRepository tokens,
      PasswordHasher passwords,
      Clock clock,
      TransactionRunner transactions) {
    return new AuthService(members, tokens, passwords, clock, transactions);
  }

  @Bean
  MemberService memberService(
      MemberRepository members, Clock clock, TransactionRunner transactions) {
    return new MemberService(members, clock, transactions);
  }

  @Bean
  BookingService bookingService(
      WeekRepository weeks,
      SlotRepository slots,
      ReservationRepository reservations,
      ClaimRepository claims,
      MemberService members,
      TransactionRunner transactions,
      Clock clock,
      @Value("${app.booking.start-minute:540}") int startMinute,
      @Value("${app.booking.end-minute:1350}") int endMinute) {
    return new BookingService(weeks, slots, reservations, claims, members, transactions, clock,
        new WeeklySchedule(startMinute, endMinute));
  }

  @Bean
  AnnouncementService announcementService(
      DeliveryRepository deliveries,
      WeekRepository weeks,
      SlotRepository slots,
      AnnouncementSender sender,
      Clock clock,
      @Value("${app.announcements-enabled}") boolean enabled,
      @Value("${app.base-url}") String baseUrl,
      @Value("${app.announcement-cron}") String cron) {
    return new AnnouncementService(
        deliveries,
        weeks,
        slots,
        sender,
        clock,
        enabled,
        baseUrl,
        CronExpression.parse(cron)::next);
  }
}
