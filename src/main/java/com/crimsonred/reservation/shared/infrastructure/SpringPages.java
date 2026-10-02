package com.crimsonred.reservation.shared.infrastructure;

import com.crimsonred.reservation.shared.application.*;
import org.springframework.data.domain.*;

public final class SpringPages {
  private SpringPages() {}

  public static Pageable request(PageQuery query) {
    var sort = Sort.by(query.sortBy()).descending();
    if (!query.sortBy().equals("id")) sort = sort.and(Sort.by("id").descending());
    return PageRequest.of(query.number(), query.size(), sort);
  }

  public static <T> PageResult<T> result(Page<T> page, PageQuery query) {
    return new PageResult<>(page.getContent(), query, page.getTotalElements());
  }
}
