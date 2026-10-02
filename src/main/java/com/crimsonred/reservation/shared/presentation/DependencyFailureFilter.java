package com.crimsonred.reservation.shared.presentation;

import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.List;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.*;
import org.springframework.dao.DataAccessException;
import org.springframework.transaction.CannotCreateTransactionException;
import org.springframework.transaction.TransactionSystemException;
import org.springframework.transaction.TransactionTimedOutException;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.databind.ObjectMapper;

@Configuration
public class DependencyFailureFilter {
  @Bean
  FilterRegistrationBean<OncePerRequestFilter> dependencyErrors(ObjectMapper json) {
    var filter =
        new OncePerRequestFilter() {
          @Override
          protected void doFilterInternal(
              HttpServletRequest req, HttpServletResponse res, FilterChain chain)
              throws ServletException, IOException {
            try {
              chain.doFilter(req, res);
            } catch (Exception ex) {
              Throwable cause = ex;
              while (cause != null && !(cause instanceof DataAccessException)
                  && !(cause instanceof CannotCreateTransactionException)
                  && !(cause instanceof TransactionSystemException)
                  && !(cause instanceof TransactionTimedOutException))
                cause = cause.getCause();
              if (cause == null) {
                if (ex instanceof ServletException s) throw s;
                if (ex instanceof IOException i) throw i;
                if (ex instanceof RuntimeException r) throw r;
                throw new ServletException(ex);
              }
              if (!res.isCommitted()) {
                var headers = new LinkedHashMap<String, List<String>>();
                for (String name : res.getHeaderNames())
                  if (!name.equalsIgnoreCase("Content-Length") && !name.equalsIgnoreCase("Content-Type"))
                    headers.put(name, List.copyOf(res.getHeaders(name)));
                // Reset writer/stream state and partial content, while keeping security headers.
                res.reset();
                headers.forEach((name, values) -> values.forEach(value -> res.addHeader(name, value)));
                res.setStatus(503);
                res.setContentType("application/json;charset=UTF-8");
                res.getWriter()
                    .write(
                        json.writeValueAsString(
                            ApiExceptionHandler.body(
                                "DEPENDENCY_UNAVAILABLE", "잠시 후 다시 시도해주세요.", Map.of())));
              }
            }
          }
        };
    var bean = new FilterRegistrationBean<OncePerRequestFilter>(filter);
    bean.setOrder(Integer.MIN_VALUE);
    return bean;
  }
}
