package com.pawcycle.backend.commerce.billing.persistence;

import com.pawcycle.backend.commerce.BillingPaymentMethodEntity;
import com.pawcycle.backend.commerce.BillingPaymentMethodPreparationEntity;
import com.pawcycle.backend.commerce.BillingPaymentMethodPreparationRepository;
import com.pawcycle.backend.commerce.BillingPaymentMethodRepository;
import com.pawcycle.backend.commerce.CommerceException;
import jakarta.persistence.EntityManager;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class BillingPersistenceAdapter {
  private final BillingPaymentMethodPreparationRepository preparations;
  private final BillingPaymentMethodRepository billingMethods;
  private final EntityManager entityManager;
  private final Clock clock;

  public BillingPersistenceAdapter(
      BillingPaymentMethodPreparationRepository preparations,
      BillingPaymentMethodRepository billingMethods,
      EntityManager entityManager,
      Clock clock) {
    this.preparations = preparations;
    this.billingMethods = billingMethods;
    this.entityManager = entityManager;
    this.clock = clock;
  }

  @Transactional
  public String createPreparation(long memberId) {
    String token = "bp-" + UUID.randomUUID();
    preparations.saveAndFlush(
        new BillingPaymentMethodPreparationEntity(
            token,
            memberId,
            "cust-" + UUID.randomUUID(),
            now().plus(10, ChronoUnit.MINUTES)));
    return token;
  }

  @Transactional
  public ClaimedPreparation claim(long memberId, String prepareToken) {
    LocalDateTime current = now();
    int claimed = preparations.claimReady(prepareToken, memberId, current, current);
    if (claimed != 1) {
      BillingPaymentMethodPreparationEntity preparation =
          preparations.findForUpdate(prepareToken, memberId).orElse(null);
      if (preparation != null && "PROCESSING".equals(preparation.getStatus()))
        throw new CommerceException(
            409,
            "BILLING_PREPARATION_IN_PROGRESS",
            "동일 Billing 준비 요청이 이미 Provider 처리 중입니다.");
      throw new CommerceException(
          409, "BILLING_PREPARATION_INVALID", "Billing 준비 정보가 유효하지 않습니다.");
    }
    BillingPaymentMethodPreparationEntity preparation =
        preparations.findForUpdate(prepareToken, memberId).orElseThrow(() -> invalidPreparation());
    return new ClaimedPreparation(preparation.getCustomerKey());
  }

  @Transactional
  public void register(long memberId, String prepareToken, String billingKey) {
    BillingPaymentMethodPreparationEntity preparation =
        preparations
            .findForUpdate(prepareToken, memberId)
            .filter(value -> "PROCESSING".equals(value.getStatus()))
            .orElseThrow(this::invalidPreparation);
    LocalDateTime current = now();
    billingMethods.revokeActive(memberId, current);
    billingMethods.saveAndFlush(
        new BillingPaymentMethodEntity(memberId, preparation.getCustomerKey(), billingKey, current));
    preparations.delete(preparation);
    preparations.flush();
    // The schedule tables still have no aggregate mapping; keep this atomic cross-table update in JPA.
    entityManager
        .createNativeQuery(
            "UPDATE subscription_schedules schedule JOIN subscriptions subscription ON subscription.id=schedule.subscription_id "
                + "LEFT JOIN subscription_order_context context ON context.schedule_id=schedule.id "
                + "SET schedule.status='SCHEDULED',schedule.hold_reason=NULL "
                + "WHERE subscription.member_id=:memberId AND subscription.status='ACTIVE' "
                + "AND schedule.status='HELD' AND schedule.hold_reason='MISSING_BILLING_METHOD' "
                + "AND context.order_id IS NULL")
        .setParameter("memberId", memberId)
        .executeUpdate();
  }

  private CommerceException invalidPreparation() {
    return new CommerceException(
        409, "BILLING_PREPARATION_INVALID", "Billing 준비 정보가 유효하지 않습니다.");
  }

  private LocalDateTime now() {
    return LocalDateTime.ofInstant(clock.instant(), ZoneId.systemDefault());
  }

  public record ClaimedPreparation(String customerKey) {}
}
