package com.crimsonred.reservation.auth.presentation;

import com.crimsonred.reservation.auth.application.AuthService;
import com.crimsonred.reservation.auth.application.port.AuthRateLimiter;
import com.crimsonred.reservation.auth.application.port.VerificationEmailSender;
import com.crimsonred.reservation.auth.domain.PasswordPolicy;
import com.crimsonred.reservation.member.domain.Member;
import com.crimsonred.reservation.member.application.MemberService;
import com.crimsonred.reservation.shared.domain.InputText;
import jakarta.servlet.http.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.*;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.*;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.csrf.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {
  private final AuthService auth;
  private final AuthRateLimiter limiter;
  private final VerificationEmailSender mail;
  private final MemberService members;
  private final HttpSessionSecurityContextRepository contexts;
  private final HttpSessionCsrfTokenRepository csrf;
  private final ClientIp clientIp;

  public AuthController(
      AuthService auth,
      AuthRateLimiter limiter,
      VerificationEmailSender mail,
      MemberService members,
      HttpSessionSecurityContextRepository contexts,
      HttpSessionCsrfTokenRepository csrf,
      ClientIp clientIp) {
    this.auth = auth;
    this.limiter = limiter;
    this.mail = mail;
    this.members = members;
    this.contexts = contexts;
    this.csrf = csrf;
    this.clientIp = clientIp;
  }

  public record Signup(
      @NotBlank @Size(max = 254) String email,
      @NotBlank @Size(max = Member.NAME_MAX_LENGTH, message = "이름은 최대 10자입니다.")
      @Pattern(regexp = InputText.SINGLE_LINE_PATTERN, message = "이름에 제어문자나 보이지 않는 문자를 사용할 수 없습니다.") String name,
      @NotBlank @Size(min = 8, max = 128)
      @Pattern(regexp = PasswordPolicy.SIGNUP_PATTERN, message = PasswordPolicy.SIGNUP_MESSAGE) String password) {}

  public record Login(
      @NotBlank @Size(max = 254) String email,
      @NotBlank @Size(min = 1, max = 128) String password) {}

  public record Email(@NotBlank @Size(max = 254) String email) {}

  public record Token(@NotBlank @Size(min = 43, max = 43) String token) {}

  public record Csrf(String token, String headerName) {}

  @GetMapping("/csrf")
  Csrf csrf(HttpServletRequest req) {
    var token = (CsrfToken) req.getAttribute(CsrfToken.class.getName());
    return new Csrf(token.getToken(), token.getHeaderName());
  }

  @PostMapping("/signup")
  Map<String, String> signup(@Valid @RequestBody Signup input, HttpServletRequest req) {
    String email = AuthService.normalize(input.email());
    limiter.mail(clientIp.address(req), email);
    mail.send(auth.signup(email, input.name(), input.password()));
    return Map.of("message", "가입 가능한 이메일이면 인증 메일을 발송했습니다.");
  }

  @PostMapping("/login")
  MemberService.View login(
      @Valid @RequestBody Login input, HttpServletRequest req, HttpServletResponse res) {
    String email = AuthService.normalize(input.email());
    limiter.login(clientIp.address(req), email);
    var m = auth.login(email, input.password());
    req.getSession();
    req.changeSessionId();
    var context = SecurityContextHolder.createEmptyContext();
    context.setAuthentication(
        UsernamePasswordAuthenticationToken.authenticated(
            m.getId(), null, List.of(new SimpleGrantedAuthority("ROLE_USER"))));
    SecurityContextHolder.setContext(context);
    contexts.saveContext(context, req, res);
    csrf.saveToken(null, req, res);
    return members.view(m);
  }

  @PostMapping("/logout")
  Map<String, String> logout(HttpServletRequest req, HttpServletResponse res) {
    var session = req.getSession(false);
    if (session != null) session.invalidate();
    SecurityContextHolder.clearContext();
    return Map.of("message", "로그아웃되었습니다.");
  }

  @PostMapping("/verification/resend")
  Map<String, String> resend(@Valid @RequestBody Email input, HttpServletRequest req) {
    String email = AuthService.normalize(input.email());
    limiter.mail(clientIp.address(req), email);
    mail.send(auth.resend(email));
    return Map.of("message", "인증이 필요한 이메일이면 메일을 발송했습니다.");
  }

  @PostMapping("/verification/confirm")
  Map<String, String> verify(@Valid @RequestBody Token input) {
    auth.verify(input.token());
    return Map.of("message", "이메일 인증을 완료했습니다.");
  }
}
