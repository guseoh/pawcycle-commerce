package com.pawcycle.backend.subscription.application;

import com.pawcycle.backend.subscription.SubscriptionApiException;
import com.pawcycle.backend.subscription.api.SubscriptionCycleSuggestionResponse;
import com.pawcycle.backend.subscription.persistence.RepeatCommerceQueryRepository;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SubscriptionCycleSuggestionApplicationService {
  private final RepeatCommerceQueryRepository queries;

  public SubscriptionCycleSuggestionApplicationService(RepeatCommerceQueryRepository queries) {
    this.queries = queries;
  }

  @Transactional(readOnly = true)
  public SubscriptionCycleSuggestionResponse cycleSuggestion(long memberId, long subscriptionId) {
    RepeatCommerceQueryRepository.SubscriptionRow subscription =
        queries
            .findRuntimeSubscription(memberId, subscriptionId)
            .orElseThrow(
                () ->
                    new SubscriptionApiException(
                        404, "SUBSCRIPTION_NOT_FOUND", "Subscription을 찾을 수 없습니다."));
    if (!"ACTIVE".equals(subscription.status())) {
      throw new SubscriptionApiException(
          409, "SUBSCRIPTION_COMMAND_NOT_ALLOWED", "ACTIVE Subscription만 제안할 수 있습니다.");
    }
    List<java.time.LocalDate> dates = queries.findSuccessfulScheduleDates(subscriptionId);
    if (dates.size() < 3) {
      throw new SubscriptionApiException(
          409,
          "CYCLE_SUGGESTION_INSUFFICIENT_HISTORY",
          "성공적으로 처리된 Subscription 주문이 3회 이상 필요합니다.");
    }
    List<Long> intervals = new ArrayList<>();
    for (int i = 0; i < dates.size() - 1; i++) {
      intervals.add(
          java.time.temporal.ChronoUnit.DAYS.between(dates.get(i + 1), dates.get(i)));
    }
    intervals.sort(Long::compareTo);
    long medianDays = median(intervals);
    long medianWeeks = medianDays / 7;
    long versionId = queries.findSourcePlanVersionId(subscription.currentSnapshotId());
    List<Integer> allowed = queries.findAllowedDeliveryCycles(versionId);
    int chosen = chooseCycle(allowed, medianDays, subscription.deliveryCycleWeeks());
    return new SubscriptionCycleSuggestionResponse(
        subscriptionId,
        subscription.deliveryCycleWeeks(),
        medianWeeks,
        allowed,
        chosen == subscription.deliveryCycleWeeks()
            ? null
            : new SubscriptionCycleSuggestionResponse.Suggestion(chosen));
  }

  private static long median(List<Long> values) {
    return values.size() % 2 == 1
        ? values.get(values.size() / 2)
        : (values.get(values.size() / 2 - 1) + values.get(values.size() / 2)) / 2;
  }

  private static int chooseCycle(List<Integer> allowed, long medianDays, int current) {
    long closestDistance =
        allowed.stream()
            .mapToLong(value -> Math.abs(value * 7L - medianDays))
            .min()
            .orElse(Long.MAX_VALUE);
    List<Integer> tied =
        allowed.stream()
            .filter(value -> Math.abs(value * 7L - medianDays) == closestDistance)
            .toList();
    if (tied.contains(current)) return current;
    return tied.stream().max(Integer::compareTo).orElse(current);
  }
}
