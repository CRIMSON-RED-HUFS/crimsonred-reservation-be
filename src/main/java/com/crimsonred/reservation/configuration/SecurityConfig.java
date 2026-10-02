package com.crimsonred.reservation.configuration;

import com.crimsonred.reservation.shared.presentation.ApiExceptionHandler;
import java.util.Map;
import org.springframework.context.annotation.*;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.*;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.csrf.*;
import tools.jackson.databind.ObjectMapper;

@Configuration
public class SecurityConfig {
  @Bean
  PasswordEncoder passwordEncoder() {
    return new Argon2PasswordEncoder(16, 32, 1, 19456, 2);
  }

  @Bean
  HttpSessionSecurityContextRepository contexts() {
    return new HttpSessionSecurityContextRepository();
  }

  @Bean
  HttpSessionCsrfTokenRepository csrfRepository() {
    return new HttpSessionCsrfTokenRepository();
  }

  @Bean
  SecurityFilterChain security(
      HttpSecurity http,
      ObjectMapper json,
      HttpSessionSecurityContextRepository contexts,
      HttpSessionCsrfTokenRepository csrf)
      throws Exception {
    http.securityContext(c -> c.securityContextRepository(contexts))
        .csrf(
            c ->
                c.csrfTokenRepository(csrf)
                    .csrfTokenRequestHandler(new CsrfTokenRequestAttributeHandler()))
        .authorizeHttpRequests(
            a ->
                a.requestMatchers("/api/v1/auth/**", "/actuator/health", "/error")
                    .permitAll()
                    .anyRequest()
                    .authenticated())
        .requestCache(c -> c.disable())
        .formLogin(c -> c.disable())
        .httpBasic(c -> c.disable())
        .logout(c -> c.disable())
        .exceptionHandling(
            e ->
                e.authenticationEntryPoint(
                        (req, res, ex) -> {
                          res.setStatus(401);
                          res.setContentType("application/json;charset=UTF-8");
                          res.getWriter()
                              .write(
                                  json.writeValueAsString(
                                      ApiExceptionHandler.body(
                                          "UNAUTHENTICATED", "로그인이 필요합니다.", Map.of())));
                        })
                    .accessDeniedHandler(
                        (req, res, ex) -> {
                          res.setStatus(403);
                          res.setContentType("application/json;charset=UTF-8");
                          res.getWriter()
                              .write(
                                  json.writeValueAsString(
                                      ApiExceptionHandler.body(
                                          "ACCESS_DENIED", "권한 또는 CSRF 토큰을 확인해주세요.", Map.of())));
                        }))
        .headers(
            h ->
                h.contentTypeOptions(c -> {})
                    .frameOptions(f -> f.deny())
                    .contentSecurityPolicy(
                        c -> c.policyDirectives("default-src 'none'; frame-ancestors 'none'")));
    return http.build();
  }
}
