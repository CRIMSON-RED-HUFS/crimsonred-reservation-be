package com.crimsonred.reservation.shared.presentation;

import com.crimsonred.reservation.shared.application.StorageConflict;
import com.crimsonred.reservation.shared.domain.BusinessException;
import jakarta.servlet.http.HttpServletRequest;
import java.time.DateTimeException;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.dao.DataAccessException;
import org.springframework.core.NestedExceptionUtils;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.ErrorResponse;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerMapping;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import org.springframework.web.util.DisconnectedClientHelper;
import org.springframework.transaction.CannotCreateTransactionException;
import org.springframework.transaction.TransactionSystemException;
import org.springframework.transaction.TransactionTimedOutException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@RestControllerAdvice
public class ApiExceptionHandler {
  private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);
  public record ErrorBody(
      String code, String message, Map<String, String> fieldErrors, String traceId) {}

  public static ErrorBody body(String code, String message, Map<String, String> fields) {
    return new ErrorBody(code, message, fields, UUID.randomUUID().toString());
  }

  @ExceptionHandler(BusinessException.class)
  ResponseEntity<ErrorBody> api(BusinessException e) {
    return ResponseEntity.status(
            switch (e.type) {
              case INVALID_INPUT -> 400;
              case UNAUTHENTICATED -> 401;
              case FORBIDDEN -> 403;
              case NOT_FOUND -> 404;
              case CONFLICT -> 409;
              case RATE_LIMITED -> 429;
              case UNAVAILABLE -> 503;
            })
        .body(body(e.code, e.getMessage(), Map.of()));
  }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  ResponseEntity<ErrorBody> validation(MethodArgumentNotValidException e) {
    var fields =
        e.getBindingResult().getFieldErrors().stream()
            .collect(
                Collectors.toMap(
                    f -> f.getField(),
                    f -> f.getDefaultMessage() == null ? "잘못된 입력" : f.getDefaultMessage(),
                    (a, b) -> a));
    return ResponseEntity.badRequest().body(body("INVALID_INPUT", "입력값을 확인해주세요.", fields));
  }

  @ExceptionHandler({
    HttpMessageNotReadableException.class,
    MethodArgumentTypeMismatchException.class,
    DateTimeException.class,
    IllegalArgumentException.class
  })
  ResponseEntity<ErrorBody> invalid(Exception e) {
    return ResponseEntity.badRequest().body(body("INVALID_INPUT", "입력 형식이 올바르지 않습니다.", Map.of()));
  }

  @ExceptionHandler({HttpRequestMethodNotSupportedException.class, HttpMediaTypeNotSupportedException.class})
  ResponseEntity<ErrorBody> protocol(ErrorResponse error) {
    return ResponseEntity.status(error.getStatusCode()).headers(error.getHeaders())
        .body(body("INVALID_REQUEST", "요청 메서드 또는 Content-Type을 확인해주세요.", Map.of()));
  }

  @ExceptionHandler(MissingServletRequestParameterException.class)
  ResponseEntity<ErrorBody> missingParameter(MissingServletRequestParameterException e) {
    return ResponseEntity.badRequest().body(body("INVALID_INPUT", "필수 입력값을 확인해주세요.",
        Map.of(e.getParameterName(), "필수 입력값입니다.")));
  }

  @ExceptionHandler(NoResourceFoundException.class)
  ResponseEntity<ErrorBody> missing(Exception e) {
    return ResponseEntity.status(404).body(body("NOT_FOUND", "대상을 찾을 수 없습니다.", Map.of()));
  }

  @ExceptionHandler({DataAccessException.class, StorageConflict.class,
      CannotCreateTransactionException.class, TransactionSystemException.class, TransactionTimedOutException.class})
  ResponseEntity<ErrorBody> dependency(Exception e) {
    var error = body("DEPENDENCY_UNAVAILABLE", "잠시 후 다시 시도해주세요.", Map.of());
    log.warn("Dependency failure: traceId={}, exception={}", error.traceId(), e.getClass().getSimpleName());
    return ResponseEntity.status(503)
        .body(error);
  }

  @ExceptionHandler(Exception.class)
  ResponseEntity<ErrorBody> unexpected(Exception e, HttpServletRequest request) {
    if (DisconnectedClientHelper.isClientDisconnectedException(e)) {
      log.debug("Client disconnected before the response completed.");
      return null;
    }
    // Never log request bodies, credentials, tokens or external-service URLs.
    var error = body("INTERNAL_ERROR", "요청을 처리하지 못했습니다.", Map.of());
    var cause = NestedExceptionUtils.getMostSpecificCause(e);
    var handler = request.getAttribute(HandlerMapping.BEST_MATCHING_HANDLER_ATTRIBUTE);
    log.error("Unhandled request failure: traceId={}, exception={}, rootCause={}, handler={}",
        error.traceId(), e.getClass().getSimpleName(), cause.getClass().getSimpleName(),
        handler instanceof HandlerMethod method ? method.getShortLogMessage() : "unknown");
    return ResponseEntity.internalServerError()
        .body(error);
  }
}
