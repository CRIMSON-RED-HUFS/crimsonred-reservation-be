package com.crimsonred.reservation.shared.application;

import java.util.List;
import java.util.function.Function;

public record PageResult<T>(List<T> content, PageQuery query, long totalElements) {
  public PageResult {
    content = List.copyOf(content);
  }

  public <R> PageResult<R> map(Function<T, R> mapper) {
    return new PageResult<>(content.stream().map(mapper).toList(), query, totalElements);
  }
}
