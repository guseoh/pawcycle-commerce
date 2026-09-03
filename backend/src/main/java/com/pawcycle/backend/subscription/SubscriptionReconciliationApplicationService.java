package com.pawcycle.backend.subscription;

import io.micrometer.core.instrument.Timer;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

@Service
class SubscriptionReconciliationApplicationService {
  private static final Logger log =
      LoggerFactory.getLogger(SubscriptionReconciliationApplicationService.class);
  private final SubscriptionPersistenceAdapter store;
  private final SubscriptionMetrics metrics;
  private final TransactionTemplate transaction;
  private final java.time.Clock clock;

  SubscriptionReconciliationApplicationService(
      SubscriptionPersistenceAdapter store,
      SubscriptionMetrics metrics,
      PlatformTransactionManager transactionManager,
      java.time.Clock clock) {
    this.store = store;
    this.metrics = metrics;
    this.clock = clock;
    this.transaction = new TransactionTemplate(transactionManager);
    this.transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
  }

  void reconcileActiveSubscriptions() {
    Timer.Sample sample = metrics.startReconciliation();
    int processed = 0;
    int failures = 0;
    try {
      List<Long> active = store.activeSubscriptionIds();
      for (Long subscriptionId : active) {
        processed++;
        try {
          transaction.executeWithoutResult(status -> reconcile(subscriptionId));
        } catch (RuntimeException exception) {
          failures++;
          log.error(
              "Subscription reconciliation failed; subscriptionId={}", subscriptionId, exception);
        }
      }
    } catch (RuntimeException exception) {
      failures++;
      throw exception;
    } finally {
      metrics.finishReconciliation(sample, processed, failures);
    }
  }

  private void reconcile(long subscriptionId) {
    store
        .lockActiveSubscription(subscriptionId)
        .ifPresent(
            subscription -> {
              java.time.LocalDate today =
                  java.time.LocalDate.now(clock.withZone(java.time.ZoneId.of("Asia/Seoul")));
              if (store.hasUnprocessedDueSchedule(subscription.id(), today)) return;
              java.util.List<ScheduleProjection> future =
                  store.futureSchedulesForUpdate(subscription.id(), today);
              if (future.size() == 1) return;
              if (future.size() > 1)
                throw new IllegalStateException("Subscription has multiple future Schedules");
              store
                  .lastProcessedSchedule(subscription.id())
                  .ifPresent(
                      processed -> {
                        java.time.LocalDate next =
                            SubscriptionOrderAutomationService.firstFutureDate(
                                processed.scheduledDate(), processed.deliveryCycleWeeks(), today);
                        if (!store.scheduleExists(subscription.id(), next))
                          store.insertScheduled(subscription.id(), next);
                        if (!store.incrementVersion(subscription.id(), subscription.version()))
                          throw new IllegalStateException(
                              "Subscription version changed while locked");
                      });
            });
  }
}
