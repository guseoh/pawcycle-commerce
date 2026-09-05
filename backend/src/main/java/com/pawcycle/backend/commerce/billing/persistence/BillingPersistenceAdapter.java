package com.pawcycle.backend.commerce.billing.persistence;

import com.pawcycle.backend.commerce.CommerceException;
import com.pawcycle.backend.foundation.persistence.NativeQueryExecutor;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.springframework.stereotype.Repository;

@Repository
public class BillingPersistenceAdapter {
  private final NativeQueryExecutor queries;
  private final Clock clock;

  public BillingPersistenceAdapter(NativeQueryExecutor queries, Clock clock) {
    this.queries = queries;
    this.clock = clock;
  }

  public String createPreparation(long memberId) {
    String token = "bp-" + UUID.randomUUID();
    queries.update(
        "INSERT INTO billing_payment_method_preparations(prepare_token,member_id,customer_key,expires_at) VALUES (?,?,?,?)",
        token,
        memberId,
        "cust-" + UUID.randomUUID(),
        Timestamp.from(clock.instant().plus(10, ChronoUnit.MINUTES)));
    return token;
  }

  public ClaimedPreparation claim(long memberId, String prepareToken) {
    int claimed =
        queries.update(
            "UPDATE billing_payment_method_preparations SET status='PROCESSING',claimed_at=? WHERE prepare_token=? AND member_id=? AND status='READY' AND expires_at>?",
            now(),
            prepareToken,
            memberId,
            now());
    if (claimed != 1) {
      String current =
          queries
              .query(
                  "SELECT status FROM billing_payment_method_preparations WHERE prepare_token=? AND member_id=?",
                  (rs, rowNumber) -> rs.getString("status"),
                  prepareToken,
                  memberId)
              .stream()
              .findFirst()
              .orElse(null);
      if ("PROCESSING".equals(current)) {
        throw new CommerceException(409, "BILLING_PREPARATION_IN_PROGRESS", "동일 Billing 준비 요청이 이미 Provider 처리 중입니다.");
      }
      throw new CommerceException(409, "BILLING_PREPARATION_INVALID", "Billing 준비 정보가 유효하지 않습니다.");
    }
    return queries
        .query(
            "SELECT customer_key AS customerKey FROM billing_payment_method_preparations WHERE prepare_token=? AND member_id=? AND status='PROCESSING'",
            (rs, rowNumber) -> new ClaimedPreparation(rs.getString("customerKey")),
            prepareToken,
            memberId)
        .stream()
        .findFirst()
        .orElseThrow(() -> new CommerceException(409, "BILLING_PREPARATION_INVALID", "Billing 준비 정보가 유효하지 않습니다."));
  }

  public void register(long memberId, String prepareToken, String billingKey) {
    String customerKey =
        queries
            .query(
                "SELECT customer_key AS customerKey FROM billing_payment_method_preparations WHERE prepare_token=? AND member_id=? AND status='PROCESSING' FOR UPDATE",
                (rs, rowNumber) -> rs.getString("customerKey"),
                prepareToken,
                memberId)
            .stream()
            .findFirst()
            .orElseThrow(() -> new CommerceException(409, "BILLING_PREPARATION_INVALID", "Billing 준비 정보가 유효하지 않습니다."));
    queries.update(
        "UPDATE billing_payment_methods SET status='REVOKED',revoked_at=? WHERE member_id=? AND status='ACTIVE'",
        now(),
        memberId);
    queries.update(
        "INSERT INTO billing_payment_methods(member_id,provider,customer_key,billing_key,status,created_at) VALUES (?,'TOSS',?,?,'ACTIVE',?)",
        memberId,
        customerKey,
        billingKey,
        now());
    queries.update("DELETE FROM billing_payment_method_preparations WHERE prepare_token=?", prepareToken);
    queries.update(
        "UPDATE subscription_schedules schedule JOIN subscriptions subscription ON subscription.id=schedule.subscription_id LEFT JOIN subscription_order_context context ON context.schedule_id=schedule.id SET schedule.status='SCHEDULED',schedule.hold_reason=NULL WHERE subscription.member_id=? AND subscription.status='ACTIVE' AND schedule.status='HELD' AND schedule.hold_reason='MISSING_BILLING_METHOD' AND context.order_id IS NULL",
        memberId);
  }

  private Timestamp now() {
    return Timestamp.from(clock.instant());
  }

  public record ClaimedPreparation(String customerKey) {}
}
