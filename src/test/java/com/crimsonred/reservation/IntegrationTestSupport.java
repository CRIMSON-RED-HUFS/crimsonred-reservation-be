package com.crimsonred.reservation;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.crimsonred.reservation.announcement.application.AnnouncementService;
import com.crimsonred.reservation.announcement.application.port.AnnouncementSender;
import com.crimsonred.reservation.announcement.application.port.DeliveryRepository;
import com.crimsonred.reservation.announcement.infrastructure.discord.DiscordWebhook;
import com.crimsonred.reservation.auth.application.AuthService;
import com.crimsonred.reservation.auth.infrastructure.mail.VerificationMail;
import com.crimsonred.reservation.auth.infrastructure.redis.RateLimiter;
import com.crimsonred.reservation.booking.application.BookingService;
import com.crimsonred.reservation.booking.application.BookingService.BookingInput;
import com.crimsonred.reservation.booking.application.ReservationView;
import com.crimsonred.reservation.booking.application.port.SlotRepository;
import com.crimsonred.reservation.booking.application.port.WeekRepository;
import com.crimsonred.reservation.booking.domain.BookingRules;
import com.crimsonred.reservation.booking.domain.ReservationType;
import com.crimsonred.reservation.member.application.MemberService;
import com.crimsonred.reservation.member.application.port.MemberRepository;
import com.crimsonred.reservation.member.domain.Member;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.mysql.MySQLContainer;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
abstract class IntegrationTestSupport {
  static final MySQLContainer MYSQL =
      new MySQLContainer("mysql:8.4.11").withDatabaseName("crimsonred");

  static final GenericContainer<?> REDIS =
      new GenericContainer<>("redis:8.2.10").withExposedPorts(6379);

  // Share containers for the cached Spring context; Testcontainers handles cleanup.
  static {
    MYSQL.start();
    REDIS.start();
  }

  @DynamicPropertySource
  static void properties(DynamicPropertyRegistry r) {
    r.add("spring.datasource.url", MYSQL::getJdbcUrl);
    r.add("spring.datasource.username", MYSQL::getUsername);
    r.add("spring.datasource.password", MYSQL::getPassword);
    r.add("spring.data.redis.host", REDIS::getHost);
    r.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
    r.add("spring.data.redis.password", () -> "");
    r.add("server.servlet.session.cookie.secure", () -> false);
    r.add("app.announcements-enabled", () -> false);
    r.add("spring.jpa.properties.hibernate.generate_statistics", () -> true);
  }

  @Autowired BookingService bookings;
  @Autowired MemberService memberService;
  @Autowired MemberRepository members;
  @Autowired AuthService auth;
  @Autowired PasswordEncoder passwords;
  @Autowired JdbcTemplate jdbc;
  @Autowired javax.sql.DataSource dataSource;
  @Autowired ObjectMapper json;
  @Autowired TransactionTemplate tx;
  @Autowired jakarta.persistence.EntityManagerFactory emf;
  @Autowired AnnouncementService announcements;
  @Autowired RateLimiter limiter;
  @Autowired WeekRepository weeks;
  @Autowired SlotRepository slots;
  @Autowired DeliveryRepository deliveries;
  @Autowired VerificationMail verificationMail;
  @Autowired org.springframework.session.SessionRepository sessions;
  @MockitoBean Clock clock;
  @MockitoBean JavaMailSender mail;
  @MockitoBean DiscordWebhook webhook;
  @LocalServerPort int port;
  static final Instant NOW = Instant.parse("2026-12-27T14:00:00Z");
  static final LocalDate WEEK = LocalDate.of(2027, 1, 4);
  static final String PASSWORD = "Correct-horse-battery-staple1!";
  String hash;
  Member admin, user;

  @BeforeEach
  void setup() {
    when(clock.instant()).thenReturn(NOW);
    when(clock.getZone()).thenReturn(ZoneOffset.UTC);
    when(mail.createMimeMessage()).thenAnswer(invocation ->
        new jakarta.mail.internet.MimeMessage(jakarta.mail.Session.getInstance(new Properties())));
    when(webhook.send(anyString(), anyString()))
        .thenReturn(new AnnouncementSender.Result("FAILED", null, 0, "Webhook 설정이 필요합니다."));
    jdbc.update("delete from announcement_delivery");
    jdbc.update("delete from active_slot_claim");
    jdbc.update("delete from reservation");
    jdbc.update("delete from booking_slot");
    jdbc.update("delete from booking_week");
    jdbc.update("delete from email_verification");
    jdbc.update("update member set reviewed_by=null");
    jdbc.update("delete from member");
    hash = passwords.encode(PASSWORD);
    admin = member("admin", true, true);
    user = member("user", true, false);
    bookings.ensureBookingWeek();
  }

  Member member(String name, boolean approved, boolean isAdmin) {
    var m = Member.register(name + "-" + UUID.randomUUID() + "@hufs.ac.kr", name, hash, NOW);
    if (approved) {
      m.verifyEmail(NOW);
      m.requestMembership();
      m.reviewMembership(null, true, null, NOW);
    }
    if (isAdmin) m.changeRole("ADMIN", 0);
    return members.saveAndFlush(m);
  }

  BookingInput input(int start, int end, String name) {
    return new BookingInput(WEEK, at(start), at(end), name, ReservationType.TEAM_REHEARSAL);
  }

  Instant at(int minute) {
    return WEEK.atStartOfDay(BookingRules.SEOUL).plusMinutes(minute).toInstant();
  }

  ReservationView reserve(Member m, String key, int start, int end) {
    return bookings.reserve(m.getId(), key, input(start, end, "정기 합주")).reservation();
  }

  int count(String table) {
    return jdbc.queryForObject("select count(*) from " + table, Integer.class);
  }

  record Browser(HttpClient client, String csrf) {}

  HttpClient browser() {
    return HttpClient.newBuilder()
        .cookieHandler(new CookieManager(null, CookiePolicy.ACCEPT_ALL))
        .connectTimeout(Duration.ofSeconds(3))
        .build();
  }

  HttpResponse<String> request(
      HttpClient client, String method, String path, Object body, String csrf) throws Exception {
    var b =
        HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/api/v1" + path))
            .timeout(Duration.ofSeconds(10));
    if (csrf != null) b.header("X-CSRF-TOKEN", csrf);
    if (body != null) b.header("Content-Type", "application/json");
    b.method(
        method,
        body == null
            ? HttpRequest.BodyPublishers.noBody()
            : HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body)));
    return client.send(b.build(), HttpResponse.BodyHandlers.ofString());
  }

  String csrf(HttpClient client) throws Exception {
    var res = request(client, "GET", "/auth/csrf", null, null);
    assertEquals(200, res.statusCode(), res.body());
    return json.readTree(res.body()).path("token").asString();
  }

  Browser login(Member m) throws Exception {
    var client = browser();
    var token = csrf(client);
    var res =
        request(
            client,
            "POST",
            "/auth/login",
            Map.of("email", m.getEmail(), "password", PASSWORD),
            token);
    assertEquals(200, res.statusCode(), res.body());
    return new Browser(client, csrf(client));
  }

}
