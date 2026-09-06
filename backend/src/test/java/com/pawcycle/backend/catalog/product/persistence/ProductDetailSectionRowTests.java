package com.pawcycle.backend.catalog.product.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class ProductDetailSectionRowTests {
  @Test
  void convertsDatabaseLocalDateTimeWithExplicitUtcContract() {
    ProductDetailSectionRow row =
        new ProductDetailSectionRow(
            1L,
            "title",
            "body",
            0,
            true,
            LocalDateTime.of(2026, 9, 6, 1, 2, 3),
            LocalDateTime.of(2026, 9, 6, 4, 5, 6));

    var view = row.toView();

    assertThat(view.createdAt()).isEqualTo(Instant.parse("2026-09-06T01:02:03Z"));
    assertThat(view.updatedAt()).isEqualTo(Instant.parse("2026-09-06T04:05:06Z"));
  }
}
