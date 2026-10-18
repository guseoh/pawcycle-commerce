package com.pawcycle.backend.subscription.application;

import com.pawcycle.backend.subscription.domain.SubscriptionFailure;
import com.pawcycle.backend.subscription.model.RepeatCommerceReadModel.SubscriptionOptions;
import com.pawcycle.backend.subscription.persistence.RepeatCommerceQueryRepository;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SubscriptionConversionApplicationService {
  private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
  private final RepeatCommerceQueryRepository queries;
  private final Clock clock;

  public SubscriptionConversionApplicationService(
      RepeatCommerceQueryRepository queries, Clock clock) {
    this.queries = queries;
    this.clock = clock;
  }

  @Transactional(readOnly = true)
  public SubscriptionOptions subscriptionOptions(long memberId, long orderId) {
    if (queries.findPaidOneTimeOrder(memberId, orderId).isEmpty()) {
      throw new SubscriptionFailure(SubscriptionFailure.Kind.NOT_FOUND, "ORDER_NOT_FOUND", "주문을 찾을 수 없습니다.");
    }
    java.util.Set<Long> sourceProducts = queries.findOrderProductIds(orderId);
    List<SubscriptionOptions.Option> options = new ArrayList<>();
    for (RepeatCommerceQueryRepository.PlanOptionRow row :
        queries.findAvailablePlanOptions(LocalDate.now(clock.withZone(SEOUL)), memberId)) {
      List<Long> matching = row.productIds().stream().filter(sourceProducts::contains).toList();
      if (matching.isEmpty()) continue;
      options.add(
          new SubscriptionOptions.Option(
              row.planVersionId(),
              row.planName(),
              matching,
              row.petIds(),
              row.allowedDeliveryCycleWeeks(),
              row.packagePriceKrw()));
    }
    return new SubscriptionOptions(orderId, options);
  }
}
