package com.crimsonred.reservation.shared.presentation;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import org.apache.catalina.connector.ClientAbortException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.converter.HttpMessageNotWritableException;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerMapping;

@ExtendWith(OutputCaptureExtension.class)
class ApiExceptionHandlerTest {
  @Test
  void disconnectedClientsDoNotTriggerErrorResponsesOrPrivateLogs(CapturedOutput output) {
    var request = new MockHttpServletRequest();
    request.setRequestURI("/verify?token=private-token");
    for (IOException cause : new IOException[] {
        new ClientAbortException(new IOException("private-token")),
        new IOException("Broken pipe"),
        new IOException("Connection reset by peer")}) {
      assertNull(new ApiExceptionHandler().unexpected(
          new HttpMessageNotWritableException("private-email@hufs.ac.kr", cause), request));
    }
    assertFalse(output.getAll().contains("Unhandled request failure"));
    assertFalse(output.getAll().contains("private-token"));
    assertFalse(output.getAll().contains("private-email"));
  }

  @Test
  void serializationDiagnosticsIdentifyHandlerAndCauseWithoutPrivateValues(CapturedOutput output)
      throws Exception {
    var request = new MockHttpServletRequest();
    request.setRequestURI("/verify?token=private-token");
    request.setAttribute(HandlerMapping.BEST_MATCHING_HANDLER_ATTRIBUTE,
        new HandlerMethod(this, getClass().getDeclaredMethod("endpoint")));
    var error = new HttpMessageNotWritableException("private-email@hufs.ac.kr",
        new IOException("private-token"));
    var response = new ApiExceptionHandler().unexpected(error, request);
    assertEquals(500, response.getStatusCode().value());
    assertTrue(output.getAll().contains("rootCause=IOException"));
    assertTrue(output.getAll().contains("#endpoint"));
    assertTrue(output.getAll().contains(response.getBody().traceId()));
    assertFalse(output.getAll().contains("private-token"));
    assertFalse(output.getAll().contains("private-email"));
  }

  void endpoint() {}
}
