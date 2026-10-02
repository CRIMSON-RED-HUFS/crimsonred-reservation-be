package com.crimsonred.reservation.shared.presentation;

import com.crimsonred.reservation.shared.application.PageResult;
import java.util.List;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

public final class ApiPages {
  private ApiPages() {}

  public record Response<T>(
      List<T> content, int number, int size, int totalPages, long totalElements,
      boolean first, boolean last, int numberOfElements, boolean empty) {}

  public static <T> Response<T> response(PageResult<T> result) {
    var query = result.query();
    var page = new PageImpl<>(
        result.content(),
        PageRequest.of(query.number(), query.size()),
        result.totalElements());
    return new Response<>(page.getContent(), page.getNumber(), page.getSize(), page.getTotalPages(),
        page.getTotalElements(), page.isFirst(), page.isLast(), page.getNumberOfElements(), page.isEmpty());
  }
}
