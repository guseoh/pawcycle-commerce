package com.pawcycle.backend.subscription.api;

import com.pawcycle.backend.subscription.model.SubscriptionReadModel;
import java.util.List;

public record PageResponse<T>(int page, int size, long totalElements, List<T> items) {
  public static <S, T> PageResponse<T> from(
      SubscriptionReadModel.Page<S> view, java.util.function.Function<S, T> item) {
    return new PageResponse<>(view.page(), view.size(), view.totalElements(),
        view.items() == null ? null : view.items().stream()
            .map(value -> value == null ? null : item.apply(value)).toList());
  }
}
