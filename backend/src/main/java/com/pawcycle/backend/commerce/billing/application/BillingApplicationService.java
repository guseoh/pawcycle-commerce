package com.pawcycle.backend.commerce.billing.application;

import com.pawcycle.backend.commerce.BillingPreparationResponse;
import com.pawcycle.backend.commerce.CommerceException;
import com.pawcycle.backend.commerce.TossBillingAdapter;
import com.pawcycle.backend.foundation.persistence.NativeQueryExecutor;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class BillingApplicationService {
  private static final Logger log = LoggerFactory.getLogger(BillingApplicationService.class);

  private final NativeQueryExecutor jdbc;
  private final TransactionTemplate transaction;
  private final TossBillingAdapter provider;
  private final Clock clock;

  public BillingApplicationService(
      NativeQueryExecutor jdbc,
      org.springframework.transaction.PlatformTransactionManager transactionManager,
      TossBillingAdapter provider,
      Clock clock) {
    this.jdbc = jdbc;
    this.transaction = new TransactionTemplate(transactionManager);
    this.provider = provider;
    this.clock = clock;
  }

  public BillingPreparationResponse prepare(long memberId) {
    return transaction.execute(
        status -> {
          String token = "bp-" + UUID.randomUUID();
          jdbc.update(
              "INSERT INTO"
                  + " billing_payment_method_preparations(prepare_token,member_id,customer_key,expires_at)"
                  + " VALUES (?,?,?,?)",
              token,
              memberId,
              "cust-" + UUID.randomUUID(),
              Timestamp.from(clock.instant().plus(10, ChronoUnit.MINUTES)));
          log.info("Billing method preparation created. memberId={}", memberId);
          return new BillingPreparationResponse(token);
        });
  }

  public void complete(long memberId, String prepareToken, String authKey) {
    if (!provider.isConfigured()) {
      throw new CommerceException(
          503, "PAYMENT_PROVIDER_UNAVAILABLE", "Toss Billing Provider가 현재 환경에 구성되지 않았습니다.");
    }
    if (authKey == null || authKey.isBlank()) {
      throw new CommerceException(400, "VALIDATION_FAILED", "authKey가 필요합니다.");
    }
    Map<String, Object> prepared =
        transaction.execute(
            status -> {
              int claimed =
                  jdbc.update(
                      "UPDATE billing_payment_method_preparations SET"
                          + " status='PROCESSING',claimed_at=? WHERE prepare_token=? AND"
                          + " member_id=? AND status='READY' AND expires_at>?",
                      now(),
                      prepareToken,
                      memberId,
                      now());
              if (claimed != 1) {
                Map<String, Object> current =
                    one(
                        "SELECT status,expires_at FROM billing_payment_method_preparations WHERE"
                            + " prepare_token=? AND member_id=?",
                        prepareToken,
                        memberId);
                if (current != null && "PROCESSING".equals(current.get("status"))) {
                  throw new CommerceException(
                      409,
                      "BILLING_PREPARATION_IN_PROGRESS",
                      "동일 Billing 준비 요청이 이미 Provider 처리 중입니다.");
                }
                throw new CommerceException(
                    409, "BILLING_PREPARATION_INVALID", "Billing 준비 정보가 유효하지 않습니다.");
              }
              return one(
                  "SELECT customer_key FROM billing_payment_method_preparations WHERE"
                      + " prepare_token=? AND member_id=? AND status='PROCESSING'",
                  prepareToken,
                  memberId);
            });

    // Provider I/O is deliberately outside the persistence transaction.
    String billingKey = provider.issueBillingKey((String) prepared.get("customer_key"), authKey).billingKey();
    transaction.executeWithoutResult(
        status -> {
          Map<String, Object> prep =
              one(
                  "SELECT customer_key,status FROM billing_payment_method_preparations WHERE"
                      + " prepare_token=? AND member_id=? AND status='PROCESSING' FOR UPDATE",
                  prepareToken,
                  memberId);
          if (prep == null) {
            throw new CommerceException(
                409, "BILLING_PREPARATION_INVALID", "Billing 준비 정보가 유효하지 않습니다.");
          }
          jdbc.update(
              "UPDATE billing_payment_methods SET status='REVOKED',revoked_at=? WHERE member_id=?"
                  + " AND status='ACTIVE'",
              now(),
              memberId);
          jdbc.update(
              "INSERT INTO"
                  + " billing_payment_methods(member_id,provider,customer_key,billing_key,status,created_at)"
                  + " VALUES (?,'TOSS',?,?,'ACTIVE',?)",
              memberId,
              prep.get("customer_key"),
              billingKey,
              now());
          jdbc.update(
              "DELETE FROM billing_payment_method_preparations WHERE prepare_token=?", prepareToken);
          jdbc.update(
              "UPDATE subscription_schedules schedule JOIN subscriptions subscription ON"
                  + " subscription.id=schedule.subscription_id LEFT JOIN subscription_order_context"
                  + " context ON context.schedule_id=schedule.id SET"
                  + " schedule.status='SCHEDULED',schedule.hold_reason=NULL WHERE"
                  + " subscription.member_id=? AND subscription.status='ACTIVE' AND"
                  + " schedule.status='HELD' AND schedule.hold_reason='MISSING_BILLING_METHOD' AND"
                  + " context.order_id IS NULL",
              memberId);
        });
    log.info("Billing method registration completed. memberId={}", memberId);
  }

  private Map<String, Object> one(String sql, Object... args) {
    List<Map<String, Object>> rows = jdbc.queryForList(sql, args);
    return rows.isEmpty() ? null : new LinkedHashMap<>(rows.getFirst());
  }

  private Timestamp now() {
    return Timestamp.from(clock.instant());
  }
}
