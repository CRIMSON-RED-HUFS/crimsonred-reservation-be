package com.crimsonred.reservation.shared.presentation;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.transaction.CannotCreateTransactionException;
import org.springframework.transaction.TransactionSystemException;
import tools.jackson.databind.json.JsonMapper;

class DependencyFailureFilterTest {
  @Test
  void dependencyFailureClearsPartialWriterOrStreamContentAndPreservesSecurityHeaders() throws Exception {
    for (var failure : List.of(new DataAccessResourceFailureException("private dependency details"),
        new CannotCreateTransactionException("private connection details"),
        new TransactionSystemException("private commit details"))) {
      for (boolean writer : new boolean[] {true, false}) {
        var request = new MockHttpServletRequest();
        var response = new MockHttpServletResponse();
        var filter = new DependencyFailureFilter().dependencyErrors(JsonMapper.builder().build()).getFilter();
        filter.doFilter(request, response, (req, res) -> {
          response.setHeader("X-Content-Type-Options", "nosniff");
          response.setHeader("Cache-Control", "no-store");
          response.setContentLength(1000);
          if (writer) response.getWriter().write("partial");
          else response.getOutputStream().write("partial".getBytes(StandardCharsets.UTF_8));
          throw failure;
        });
        assertEquals(503, response.getStatus());
        assertEquals("nosniff", response.getHeader("X-Content-Type-Options"));
        assertEquals("no-store", response.getHeader("Cache-Control"));
        assertNull(response.getHeader("Content-Length"));
        assertTrue(response.getContentAsString().contains("DEPENDENCY_UNAVAILABLE"));
        assertFalse(response.getContentAsString().contains("partial"));
        assertFalse(response.getContentAsString().contains("private"));
      }
    }
  }
}
