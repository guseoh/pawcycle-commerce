package com.pawcycle.backend.subscription;

import com.pawcycle.backend.subscription.persistence.SubscriptionOrderPersistence;
import com.pawcycle.backend.subscription.persistence.SubscriptionOrderPersistence.AddOnRow;
import com.pawcycle.backend.subscription.persistence.SubscriptionOrderPersistence.Candidate;
import com.pawcycle.backend.subscription.persistence.SubscriptionOrderPersistence.ScheduleRow;
import com.pawcycle.backend.subscription.persistence.SubscriptionOrderPersistence.ShippingRow;
import com.pawcycle.backend.subscription.persistence.SubscriptionOrderPersistence.SnapshotItem;
import com.pawcycle.backend.subscription.persistence.SubscriptionOrderPersistence.SnapshotRow;
import com.pawcycle.backend.subscription.persistence.SubscriptionOrderPersistence.SubscriptionRow;
import io.micrometer.core.instrument.Timer;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

@Component
public class SubscriptionOrderProcessor {

  private static final Logger log = LoggerFactory.getLogger(SubscriptionOrderProcessor.class);
  private static final String AUTOMATION_FAILURE_LOG =
      "Subscription order automation failed; subscriptionId={}, scheduleId={}, failureCategory={}";
  private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

  private final SubscriptionOrderPersistence persistence;
  private final Clock clock;
  private final SubscriptionOrderAutomationMetrics metrics;
  private final TransactionTemplate targetTransaction;

  public SubscriptionOrderProcessor(
      SubscriptionOrderPersistence persistence,
      Clock clock,
      SubscriptionOrderAutomationMetrics metrics,
      PlatformTransactionManager transactionManager) {
    this.persistence = persistence;
    this.clock = clock;
    this.metrics = metrics;
    this.targetTransaction = new TransactionTemplate(transactionManager);
    this.targetTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
  }

  public SubscriptionAutomationBatchResult processDueSchedules(int batchSize) {
    if (batchSize < 1) {
      throw new IllegalArgumentException("batchSize must be positive");
    }

    Timer.Sample sample = metrics.start();
    int processed = 0;
    int created = 0;
    int failures = 0;
    int duplicateOrNoOp = 0;
    try {
      LocalDate today = today();
      List<Candidate> candidates = persistence.findDueCandidates(today, today, batchSize);
      for (Candidate candidate : candidates) {
        processed++;
        try {
          ProcessingOutcome outcome =
              targetTransaction.execute(status -> processCandidate(candidate, today));
          if (outcome == ProcessingOutcome.CREATED) {
            created++;
          } else {
            duplicateOrNoOp++;
          }
        } catch (RuntimeException exception) {
          failures++;
          log.error(
              AUTOMATION_FAILURE_LOG,
              candidate.subscriptionId(),
              candidate.scheduleId(),
              failureCategory(exception));
        }
      }
      return new SubscriptionAutomationBatchResult(processed, created, failures, duplicateOrNoOp);
    } catch (RuntimeException exception) {
      failures++;
      log.error(
          "Subscription order automation batch failed; failureCategory={}",
          failureCategory(exception));
      throw exception;
    } finally {
      metrics.finish(sample, processed, created, failures, duplicateOrNoOp);
    }
  }

  private ProcessingOutcome processCandidate(Candidate candidate, LocalDate today) {
    var maybeSubscription = persistence.lockSubscription(candidate.subscriptionId());
    if (maybeSubscription.isEmpty()) {
      return ProcessingOutcome.NO_OP;
    }
    var subscription = maybeSubscription.get();
    if (!Boolean.TRUE.equals(subscription.runtimeManaged())
        || !"ACTIVE".equals(subscription.status())) {
      return ProcessingOutcome.NO_OP;
    }

    var maybeSchedule = persistence.lockSchedule(candidate.scheduleId());
    if (maybeSchedule.isEmpty()) {
      return ProcessingOutcome.NO_OP;
    }
    var schedule = maybeSchedule.get();
    LocalDate scheduledDate = schedule.scheduledDate();
    if (schedule.subscriptionId() != candidate.subscriptionId()
        || !isDueStatus(schedule)
        || scheduledDate.isAfter(today)) {
      return ProcessingOutcome.NO_OP;
    }
    if (orderExists(candidate.scheduleId())) {
      return ProcessingOutcome.NO_OP;
    }
    var shipping = shippingSnapshotOrDefault(candidate.subscriptionId(), subscription.memberId());
    if (shipping == null) {
      hold(candidate.scheduleId(), "MISSING_SHIPPING_ADDRESS");
      return ProcessingOutcome.NO_OP;
    }
    if (persistence.lockBillingMethod(subscription.memberId()).isEmpty()) {
      hold(candidate.scheduleId(), "MISSING_BILLING_METHOD");
      return ProcessingOutcome.NO_OP;
    }

    long currentSnapshotId = subscription.currentSnapshotId();
    var currentSnapshot = snapshot(candidate.subscriptionId(), currentSnapshotId);
    var pending = persistence.lockPendingChange(candidate.subscriptionId());
    boolean appliesPending =
        pending.map(row -> row.targetScheduleId() == candidate.scheduleId()).orElse(false);
    long effectiveSnapshotId;
    if (appliesPending) {
      effectiveSnapshotId = pending.orElseThrow().snapshotId();
    } else {
      effectiveSnapshotId = currentSnapshotId;
    }
    var effectiveSnapshot = snapshot(candidate.subscriptionId(), effectiveSnapshotId);
    int currentDeliveryCycleWeeks = currentSnapshot.deliveryCycleWeeks();
    if (subscription.deliveryCycleWeeks() != currentDeliveryCycleWeeks) {
      throw new IllegalStateException("Subscription delivery cycle differs from current snapshot");
    }
    int effectiveDeliveryCycleWeeks = effectiveSnapshot.deliveryCycleWeeks();

    var items = persistence.findSnapshotItems(effectiveSnapshotId);
    if (items.isEmpty()) {
      throw new IllegalStateException("Effective snapshot has no order items");
    }
    var addOns = persistence.lockAddOns(candidate.scheduleId());
    if (hasUnavailableStock(items, addOns)) {
      hold(candidate.scheduleId(), "ORDER_STOCK_UNAVAILABLE");
      return ProcessingOutcome.NO_OP;
    }
    if (addOns.stream()
        .anyMatch(
            addon ->
                !"ACTIVE".equals(addon.skuStatus())
                    || !"PUBLIC".equals(addon.displayStatus())
                    || !Boolean.TRUE.equals(addon.categoryActive())
                    || !Boolean.TRUE.equals(addon.brandActive()))) {
      hold(candidate.scheduleId(), "ORDER_STOCK_UNAVAILABLE");
      return ProcessingOutcome.NO_OP;
    }

    long orderId =
        createCommonOrder(subscription, schedule, effectiveSnapshot, shipping, items, addOns);
    persistence.insertSubscriptionOrder(
        subscription.memberId(),
        candidate.subscriptionId(),
        candidate.scheduleId(),
        effectiveSnapshotId,
        effectiveSnapshot.sourcePlanVersionId(),
        scheduledDate,
        LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC),
        effectiveSnapshot.packageTotalKrw().add(addOnTotal(addOns)));
    long subscriptionOrderId = persistence.lastInsertedId();
    for (var item : items) {
      persistence.insertSubscriptionOrderItem(subscriptionOrderId, item.skuId(), item.quantity());
    }
    for (var addon : addOns) {
      persistence.insertSubscriptionOrderAddOn(
          subscriptionOrderId, addon.skuId(), addon.quantity(), addon.unitPriceKrw());
    }
    persistence.deleteScheduleAddOns(candidate.scheduleId());

    persistence.setEffectiveSnapshot(effectiveSnapshotId, candidate.scheduleId());
    persistence.deleteReminder(candidate.scheduleId());
    if (appliesPending) {
      int promoted =
          persistence.promoteSnapshot(
              effectiveSnapshotId,
              effectiveDeliveryCycleWeeks,
              candidate.subscriptionId(),
              currentSnapshotId,
              currentDeliveryCycleWeeks);
      int removed =
          persistence.deletePendingChange(
              candidate.subscriptionId(), effectiveSnapshotId, candidate.scheduleId());
      if (promoted != 1 || removed != 1) {
        throw new IllegalStateException("Pending snapshot promotion lost its target");
      }
    }

    LocalDate nextScheduledDate =
        firstFutureDate(scheduledDate, effectiveDeliveryCycleWeeks, today);
    var futureSchedules = persistence.lockFutureSchedules(candidate.subscriptionId(), today);
    if (futureSchedules.isEmpty()) {
      persistence.insertFutureSchedule(candidate.subscriptionId(), nextScheduledDate);
    } else if (futureSchedules.size() != 1
        || !nextScheduledDate.equals(futureSchedules.getFirst().scheduledDate())) {
      throw new IllegalStateException("Future Schedule cardinality is not safely recoverable");
    }

    long expectedVersion = subscription.version();
    int versionUpdated = persistence.incrementVersion(candidate.subscriptionId(), expectedVersion);
    if (versionUpdated != 1) {
      throw new IllegalStateException("Subscription version changed while locked");
    }
    return ProcessingOutcome.CREATED;
  }

  private ShippingRow shippingSnapshotOrDefault(long subscriptionId, long memberId) {
    var existing = persistence.lockShippingSnapshot(subscriptionId);
    if (existing.isPresent()) return existing.get();
    var defaultAddress = persistence.lockDefaultAddress(memberId);
    if (defaultAddress.isEmpty()) return null;
    var address = defaultAddress.get();
    persistence.insertShippingSnapshot(
        subscriptionId,
        address.recipientName(),
        address.recipientPhone(),
        address.postalCode(),
        address.addressLine1(),
        address.addressLine2(),
        LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC));
    return address;
  }

  private long createCommonOrder(
      SubscriptionRow subscription,
      ScheduleRow schedule,
      SnapshotRow effectiveSnapshot,
      ShippingRow shipping,
      List<SnapshotItem> snapshotItems,
      List<AddOnRow> addOns) {
    BigDecimal total = effectiveSnapshot.packageTotalKrw().add(addOnTotal(addOns));
    String orderNumber = "SUB-" + UUID.randomUUID();
    LocalDateTime timestamp = LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
    persistence.insertOrder(
        orderNumber,
        subscription.memberId(),
        total,
        total,
        shipping.recipientName(),
        shipping.recipientPhone(),
        shipping.postalCode(),
        shipping.addressLine1(),
        shipping.addressLine2(),
        timestamp);
    long orderId = persistence.lastInsertedId();
    persistence.insertOrderContext(
        orderId,
        subscription.id(),
        schedule.id(),
        effectiveSnapshot.id(),
        effectiveSnapshot.sourcePlanVersionId(),
        schedule.scheduledDate());
    String providerOrderId = "TOSS-SUB-" + UUID.randomUUID();
    persistence.insertBillingPayment(
        orderId, total, providerOrderId, "billing-" + UUID.randomUUID(), 1, timestamp, timestamp);
    long paymentId = persistence.lastInsertedId();
    var items = persistence.findPricedSnapshotItems(effectiveSnapshot.id());
    if (items.size() != snapshotItems.size())
      throw new IllegalStateException("Subscription snapshot SKU is missing");
    for (var item : items) {
      int quantity = item.quantity();
      reserveInventory(item.skuId(), quantity, paymentId, timestamp);
      BigDecimal unitPrice = item.price();
      persistence.insertOrderItem(
          orderId,
          item.skuId(),
          item.skuCode(),
          item.productName(),
          item.skuName(),
          unitPrice,
          quantity,
          unitPrice.multiply(BigDecimal.valueOf(quantity)));
    }
    for (var addon : addOns) {
      int quantity = addon.quantity();
      BigDecimal unitPrice = addon.unitPriceKrw();
      reserveInventory(addon.skuId(), quantity, paymentId, timestamp);
      persistence.insertOrderItem(
          orderId,
          addon.skuId(),
          addon.skuCode(),
          addon.productName(),
          addon.skuName(),
          unitPrice,
          quantity,
          unitPrice.multiply(BigDecimal.valueOf(quantity)));
    }
    return orderId;
  }

  private boolean hasUnavailableStock(List<SnapshotItem> baseItems, List<AddOnRow> addOns) {
    Map<Long, Integer> quantities = new java.util.TreeMap<>();
    for (var item : baseItems) quantities.merge(item.skuId(), item.quantity(), Integer::sum);
    for (var addon : addOns) {
      long skuId = addon.skuId();
      if (quantities.containsKey(skuId))
        throw new IllegalStateException("Base plan SKU conflicts with Add-on SKU");
      quantities.put(skuId, addon.quantity());
    }
    for (Map.Entry<Long, Integer> entry : quantities.entrySet()) {
      Integer available = persistence.lockAvailableQuantity(entry.getKey());
      if (available == null || available < entry.getValue()) return true;
    }
    return false;
  }

  private BigDecimal addOnTotal(List<AddOnRow> addOns) {
    return addOns.stream()
        .map(addon -> addon.unitPriceKrw().multiply(BigDecimal.valueOf(addon.quantity())))
        .reduce(BigDecimal.ZERO, BigDecimal::add);
  }

  private static boolean isDueStatus(ScheduleRow schedule) {
    return "SCHEDULED".equals(schedule.status())
        || ("HELD".equals(schedule.status())
            && "ORDER_STOCK_UNAVAILABLE".equals(schedule.holdReason()));
  }

  private void reserveInventory(long skuId, int quantity, long paymentId, LocalDateTime timestamp) {
    var inventory =
        persistence
            .findInventory(skuId)
            .orElseThrow(() -> new IllegalStateException("Inventory is missing"));
    long available = inventory.availableQuantity();
    long reserved = inventory.reservedQuantity();
    long version = inventory.version();
    if (available < quantity
        || persistence.reserveInventory(quantity, quantity, skuId, version, quantity) != 1) {
      throw new IllegalStateException("Inventory reservation conflict");
    }
    persistence.insertReservationMovement(
        skuId,
        paymentId,
        quantity,
        available,
        available - quantity,
        reserved,
        reserved + quantity,
        timestamp);
  }

  private void hold(long scheduleId, String reason) {
    persistence.holdSchedule(reason, scheduleId);
  }

  static LocalDate firstFutureDate(
      LocalDate scheduledDate, int deliveryCycleWeeks, LocalDate today) {
    LocalDate next = scheduledDate;
    do {
      next = next.plusWeeks(deliveryCycleWeeks);
    } while (!next.isAfter(today));
    return next;
  }

  private SnapshotRow snapshot(long subscriptionId, long snapshotId) {
    return persistence
        .findSnapshot(snapshotId, subscriptionId)
        .orElseThrow(() -> new IllegalStateException("Subscription snapshot is missing"));
  }

  private boolean orderExists(long scheduleId) {
    return !persistence.lockExistingOrders(scheduleId).isEmpty();
  }

  private LocalDate today() {
    return LocalDate.ofInstant(clock.instant(), SEOUL);
  }

  private static String failureCategory(RuntimeException exception) {
    if (exception instanceof DataIntegrityViolationException) {
      return "DATA_INTEGRITY";
    }
    if (exception instanceof DataAccessException) {
      return "DATABASE";
    }
    if (exception instanceof IllegalStateException) {
      return "INVARIANT";
    }
    return "UNEXPECTED";
  }

  private enum ProcessingOutcome {
    CREATED,
    NO_OP
  }
}
