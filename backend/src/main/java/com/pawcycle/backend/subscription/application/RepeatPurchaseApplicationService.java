package com.pawcycle.backend.subscription.application;

import com.pawcycle.backend.subscription.api.RepeatPurchaseResponse;
import com.pawcycle.backend.subscription.persistence.RepeatCommerceQueryRepository;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RepeatPurchaseApplicationService {
  private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
  private final RepeatCommerceQueryRepository queries;
  private final Clock clock;

  public RepeatPurchaseApplicationService(RepeatCommerceQueryRepository queries, Clock clock) {
    this.queries = queries;
    this.clock = clock;
  }

  @Transactional(readOnly = true)
  public RepeatPurchaseResponse reorderTiming(long memberId) {
    LocalDate today = LocalDate.now(clock.withZone(SEOUL));
    Map<Long, ProductDates> products = new LinkedHashMap<>();
    for (RepeatCommerceQueryRepository.PurchaseRow row : queries.findOneTimePurchaseDates(memberId)) {
      products.computeIfAbsent(row.productId(), id -> new ProductDates(id, row.productName()))
          .dates()
          .add(row.purchasedDate());
    }
    List<RepeatPurchaseResponse.Item> result = new ArrayList<>();
    for (ProductDates product : products.values()) {
      List<LocalDate> dates =
          product.dates().stream().sorted(Comparator.reverseOrder()).limit(5).toList();
      if (dates.size() < 3) continue;
      List<Long> intervals = new ArrayList<>();
      for (int i = 0; i < dates.size() - 1; i++) {
        intervals.add(
            java.time.temporal.ChronoUnit.DAYS.between(dates.get(i + 1), dates.get(i)));
      }
      intervals.sort(Long::compareTo);
      long median = median(intervals);
      LocalDate expected = dates.getFirst().plusDays(median);
      if (expected.isAfter(today.plusDays(7))) continue;
      result.add(
          new RepeatPurchaseResponse.Item(
              product.id(),
              product.name(),
              dates.getFirst(),
              expected,
              expected.isBefore(today) ? "OVERDUE" : "DUE_SOON",
              dates.size()));
    }
    result.sort(Comparator.comparing(RepeatPurchaseResponse.Item::expectedReorderDate));
    return new RepeatPurchaseResponse(result.stream().limit(10).toList());
  }

  private static long median(List<Long> values) {
    return values.size() % 2 == 1
        ? values.get(values.size() / 2)
        : (values.get(values.size() / 2 - 1) + values.get(values.size() / 2)) / 2;
  }

  private static final class ProductDates {
    private final long id;
    private final String name;
    private final List<LocalDate> dates = new ArrayList<>();

    private ProductDates(long id, String name) {
      this.id = id;
      this.name = name;
    }

    private long id() {
      return id;
    }

    private String name() {
      return name;
    }

    private List<LocalDate> dates() {
      return dates;
    }
  }
}
