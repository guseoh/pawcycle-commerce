package com.pawcycle.backend.commerce;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.pawcycle.backend.commerce.audit.application.AdminAuditService;
import com.pawcycle.backend.commerce.cancellation.application.CancellationService;
import com.pawcycle.backend.commerce.common.error.CommerceException;
import com.pawcycle.backend.commerce.refund.application.RefundService;
import com.pawcycle.backend.commerce.refund.infrastructure.toss.TossRefundAdapter;
import com.pawcycle.backend.commerce.refund.persistence.RefundPersistenceAdapter;
import com.pawcycle.backend.commerce.returning.application.ReturnService;
import com.pawcycle.backend.support.AfterSalesFixtures;
import com.pawcycle.backend.support.AfterSalesFixtures.OrderFixture;
import com.pawcycle.backend.member.application.AuthenticatedMemberPrincipal;
import com.pawcycle.backend.member.domain.MemberRole;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import tools.jackson.databind.ObjectMapper;

/** Real recovery transactions; only external Toss I/O is mocked. */
@SpringBootTest
@ActiveProfiles({"test", "local-integration"})
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class RefundRecoveryIntegrationTests {
  @Autowired private RefundService refunds;
  @Autowired private RefundPersistenceAdapter rows;
  @Autowired private CancellationService cancellations;
  @Autowired private ReturnService returns;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private EntityManager entities;
  @Autowired private PlatformTransactionManager manager;
  @Autowired private WebApplicationContext context;
  @Autowired private ObjectMapper json;
  @MockitoBean private TossRefundAdapter provider;
  @MockitoSpyBean private AdminAuditService audits;
  private AfterSalesFixtures f;

  @BeforeEach void fixtures() { f = new AfterSalesFixtures(jdbc, entities, manager); }
  private OrderFixture cancelled(BigDecimal amount) {
    OrderFixture o = f.paidOrder("PREPARING", amount);
    cancellations.request(o.member(), o.order(), "refund fixture");
    return o;
  }
  private long refund(OrderFixture o) { return f.id("SELECT id FROM refunds WHERE order_id=? AND attempt_no=1", o.order()); }

  @Test
  void simultaneousProcessSeesCommittedProcessingAndCallsProviderOnlyOnce() throws Exception {
    OrderFixture o = cancelled(new BigDecimal("123.45"));
    long id = refund(o);
    CountDownLatch entered = new CountDownLatch(1);
    CountDownLatch release = new CountDownLatch(1);
    when(provider.isConfigured()).thenReturn(true);
    when(provider.refund(anyString(), any())).thenAnswer(invocation -> {
      assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
      assertThat(f.text("SELECT status FROM refunds WHERE id=?", id)).isEqualTo("PROCESSING");
      assertThat((BigDecimal) invocation.getArgument(1)).isEqualByComparingTo("123.45");
      assertThat((String) invocation.getArgument(0)).isEqualTo(f.text("SELECT idempotency_key FROM refunds WHERE id=?", id));
      entered.countDown();
      assertThat(release.await(10, TimeUnit.SECONDS)).isTrue();
      return new TossRefundAdapter.RefundResult("SUCCEEDED", "DONE_DYNAMIC");
    });
    try (var executor = Executors.newSingleThreadExecutor()) {
      var first = executor.submit(() -> refunds.process(id, o.admin()));
      try {
        assertThat(entered.await(10, TimeUnit.SECONDS)).isTrue();
        assertError(() -> refunds.process(id, o.admin()), 409, "REFUND_STATE_CONFLICT");
      } finally { release.countDown(); }
      assertThat(first.get(20, TimeUnit.SECONDS).amount()).isEqualByComparingTo("123.45");
    }
    org.mockito.Mockito.verify(provider, org.mockito.Mockito.times(1)).refund(anyString(), any());
    assertCompleted(o, id, "CANCELLATION");
    assertThat(f.text("SELECT provider_status FROM refunds WHERE id=?", id)).isEqualTo("DONE_DYNAMIC");
  }

  @ParameterizedTest
  @ValueSource(strings = {"UNKNOWN", "PROCESSING"})
  void overlappingReconciliationCompletesEffectsOnceAndPreservesTwoCommittedAttempts(String initial) throws Exception {
    OrderFixture o = cancelled(BigDecimal.ONE);
    long id = refund(o);
    jdbc.update("UPDATE refunds SET status=? WHERE id=?", initial, id);
    CountDownLatch both = new CountDownLatch(2);
    when(provider.isConfigured()).thenReturn(true);
    when(provider.reconcile(anyString())).thenAnswer(invocation -> {
      assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
      both.countDown();
      assertThat(both.await(10, TimeUnit.SECONDS)).isTrue();
      assertThat(f.integer("SELECT reconciliation_attempts FROM refunds WHERE id=?", id)).isEqualTo(2);
      return new TossRefundAdapter.RefundResult("SUCCEEDED", "RECOVERED");
    });
    var results = AfterSalesFixtures.race(() -> refunds.reconcile(id, o.admin()), () -> refunds.reconcile(id, o.admin()));
    assertThat(results).allSatisfy(r -> assertThat(r.error()).isNull());
    assertThat(f.integer("SELECT reconciliation_attempts FROM refunds WHERE id=?", id)).isEqualTo(2);
    assertCompleted(o, id, "CANCELLATION");
    assertThat(f.integer("SELECT COUNT(*) FROM admin_audit_logs WHERE target_id=? AND action='REFUND_RECONCILE'", id)).isEqualTo(1);
  }

  @Test
  void competingRetriesReplayOneNewAttemptWithDistinctIdempotencyKeyAndCapAtThree() throws Exception {
    OrderFixture o = cancelled(BigDecimal.ONE);
    long id = refund(o);
    jdbc.update("UPDATE refunds SET status='FAILED' WHERE id=?", id);
    var results = AfterSalesFixtures.race(() -> refunds.retry(id, o.admin()), () -> refunds.retry(id, o.admin()));
    assertThat(results).allSatisfy(r -> assertThat(r.error()).isNull());
    assertThat(results.get(0).value()).isEqualTo(results.get(1).value());
    assertThat(f.integer("SELECT COUNT(*) FROM refunds WHERE order_id=?", o.order())).isEqualTo(2);
    assertThat(f.integer("SELECT COUNT(DISTINCT idempotency_key) FROM refunds WHERE order_id=?", o.order())).isEqualTo(2);
    long second = f.id("SELECT id FROM refunds WHERE order_id=? AND attempt_no=2", o.order());
    assertThat(f.id("SELECT source_id FROM refunds WHERE id=?", second)).isEqualTo(f.id("SELECT cancellation_id FROM refunds WHERE id=?", id));
    assertThat(jdbc.queryForObject("SELECT return_id FROM refunds WHERE id=?", Long.class, second)).isNull();
    assertThat(f.integer("SELECT COUNT(*) FROM admin_audit_logs WHERE target_id=? AND action='REFUND_RETRY'", second)).isEqualTo(1);
    jdbc.update("UPDATE refunds SET status='FAILED' WHERE id=?", second);
    long third = refunds.retry(second).refundId();
    jdbc.update("UPDATE refunds SET status='FAILED' WHERE id=?", third);
    assertError(() -> refunds.retry(third), 409, "REFUND_RETRY_NOT_ALLOWED");
    assertThat(f.integer("SELECT COUNT(*) FROM refunds WHERE order_id=?", o.order())).isEqualTo(3);
    verifyNoInteractions(provider);
  }

  @Test
  void retryAuditFailureRollsBackNewAttemptAndNextRetryStillWorks() {
    OrderFixture o = cancelled(BigDecimal.ONE);
    long id = refund(o);
    jdbc.update("UPDATE refunds SET status='FAILED' WHERE id=?", id);
    doThrow(new IllegalStateException("retry audit failure")).when(audits).append(
        org.mockito.ArgumentMatchers.eq(o.admin()), org.mockito.ArgumentMatchers.eq("REFUND_RETRY"),
        org.mockito.ArgumentMatchers.eq("REFUND"), org.mockito.ArgumentMatchers.anyLong());
    assertThatThrownBy(() -> refunds.retry(id, o.admin())).isInstanceOf(IllegalStateException.class);
    assertThat(f.integer("SELECT COUNT(*) FROM refunds WHERE order_id=?", o.order())).isEqualTo(1);
    assertThat(refunds.retry(id).attemptNo()).isEqualTo(2);
  }

  @Test
  void failedProviderResultKeepsCompensationPendingAndRecordsFailureCode() {
    OrderFixture o = cancelled(BigDecimal.ONE);
    long id = refund(o);
    when(provider.isConfigured()).thenReturn(true);
    when(provider.refund(anyString(), any())).thenReturn(new TossRefundAdapter.RefundResult("FAILED", "DECLINED"));
    assertThat(refunds.process(id, o.admin()).failureCode()).isEqualTo("TOSS_REJECTED");
    assertThat(f.text("SELECT status FROM refunds WHERE id=?", id)).isEqualTo("FAILED");
    assertThat(f.text("SELECT status FROM order_cancellations WHERE order_id=?", o.order())).isEqualTo("REFUND_PENDING");
    assertThat(f.text("SELECT status FROM member_coupons WHERE id=?", o.coupon())).isEqualTo("USED");
    assertThat(f.integer("SELECT COUNT(*) FROM notifications WHERE member_id=? AND reference_id=? AND reference_type='REFUND'", o.member(), id)).isZero();
    assertThat(f.integer("SELECT available_quantity FROM inventories WHERE sku_id=?", o.firstSku())).isEqualTo(12);
  }

  @Test
  void unconfiguredProviderLeavesCommittedProcessingAndRecoveryUsesReconcile() {
    OrderFixture o = cancelled(BigDecimal.ONE);
    long id = refund(o);
    when(provider.isConfigured()).thenReturn(false);
    assertError(() -> refunds.process(id), 503, "REFUND_PROVIDER_UNAVAILABLE");
    assertThat(f.text("SELECT status FROM refunds WHERE id=?", id)).isEqualTo("PROCESSING");
    assertThat(jdbc.queryForObject("SELECT processed_at FROM refunds WHERE id=?", Timestamp.class, id)).isNotNull();
    when(provider.isConfigured()).thenReturn(true);
    when(provider.reconcile(anyString())).thenReturn(new TossRefundAdapter.RefundResult("SUCCEEDED", "RECOVERED"));
    assertThat(refunds.reconcile(id).status()).isEqualTo("SUCCEEDED");
    assertCompleted(o, id, "CANCELLATION");
  }

  @Test
  void timeoutPreservesProcessingAndUnknownRecoveryAtCapDeduplicatesActionNotification() {
    OrderFixture o = cancelled(BigDecimal.ONE);
    long id = refund(o);
    when(provider.isConfigured()).thenReturn(true);
    when(provider.refund(anyString(), any())).thenThrow(new IllegalStateException("fixture timeout"));
    assertThat(refunds.process(id).status()).isEqualTo("PROCESSING");
    assertThat(f.text("SELECT provider_status FROM refunds WHERE id=?", id)).isEqualTo("NO_RESPONSE");
    jdbc.update("UPDATE refunds SET status='UNKNOWN',reconciliation_attempts=8 WHERE id=?", id);
    when(provider.reconcile(anyString())).thenReturn(new TossRefundAdapter.RefundResult("UNKNOWN", "NO_RESPONSE"));
    assertThat(refunds.reconcile(id).status()).isEqualTo("UNKNOWN");
    assertThat(refunds.reconcile(id).reconciliationAttempts()).isEqualTo(10);
    assertThat(f.integer("SELECT COUNT(*) FROM notifications WHERE member_id=? AND type='REFUND_ACTION_REQUIRED' AND reference_id=?", o.member(), id)).isEqualTo(1);
    assertError(() -> refunds.reconcile(id), 409, "REFUND_RECONCILIATION_EXHAUSTED");
  }

  @ParameterizedTest
  @ValueSource(strings = {"READY", "FAILED", "SUCCEEDED"})
  void reconciliationRejectsNonRecoveryStatesWithoutIncreasingAttempts(String state) {
    OrderFixture o = cancelled(BigDecimal.ONE);
    long id = refund(o);
    jdbc.update("UPDATE refunds SET status=? WHERE id=?", state, id);
    when(provider.isConfigured()).thenReturn(true);
    assertError(() -> refunds.reconcile(id), 409, "REFUND_RECONCILIATION_NOT_ALLOWED");
    assertThat(f.integer("SELECT reconciliation_attempts FROM refunds WHERE id=?", id)).isZero();
  }

  @Test
  void completionAuditFailureRollsBackEffectsButKeepsProcessingThenRecoversWithoutNewRefund() {
    OrderFixture o = cancelled(BigDecimal.ONE);
    long id = refund(o);
    when(provider.isConfigured()).thenReturn(true);
    when(provider.refund(anyString(), any())).thenReturn(new TossRefundAdapter.RefundResult("SUCCEEDED", "DONE"));
    doThrow(new IllegalStateException("completion audit failure")).when(audits).append(o.admin(), "REFUND_PROCESS", "REFUND", id);
    assertThatThrownBy(() -> refunds.process(id, o.admin())).isInstanceOf(IllegalStateException.class);
    assertThat(f.text("SELECT status FROM refunds WHERE id=?", id)).isEqualTo("PROCESSING");
    assertThat(jdbc.queryForObject("SELECT completed_at FROM refunds WHERE id=?", Timestamp.class, id)).isNull();
    assertThat(f.text("SELECT status FROM order_cancellations WHERE order_id=?", o.order())).isEqualTo("REFUND_PENDING");
    assertThat(f.text("SELECT status FROM member_coupons WHERE id=?", o.coupon())).isEqualTo("USED");
    assertThat(f.integer("SELECT COUNT(*) FROM member_memberships WHERE member_id=?", o.member())).isZero();
    assertThat(f.integer("SELECT COUNT(*) FROM notifications WHERE member_id=? AND reference_type='REFUND' AND reference_id=?", o.member(), id)).isZero();
    when(provider.reconcile(anyString())).thenReturn(new TossRefundAdapter.RefundResult("SUCCEEDED", "DONE"));
    assertThat(refunds.reconcile(id, o.admin()).status()).isEqualTo("SUCCEEDED");
    assertCompleted(o, id, "CANCELLATION");
  }

  @Test
  void zeroAmountCompletesCancellationWithoutAnyProviderInteraction() {
    OrderFixture o = cancelled(BigDecimal.ZERO);
    long id = refund(o);
    assertThat(refunds.process(id).providerStatus()).isEqualTo("ZERO_AMOUNT");
    assertCompleted(o, id, "CANCELLATION");
    verifyNoInteractions(provider);
  }

  @Test
  void returnRefundCompletesNullableCancellationSourceAndDoesNotRestoreStockAgain() {
    OrderFixture o = f.paidOrder("DELIVERED", new BigDecimal("12.34"));
    long returnId = returns.request(o.member(), o.order(), "refund return").returnId();
    returns.approve(o.admin(), returnId);
    returns.receive(o.admin(), returnId, true);
    long id = refund(o);
    assertThat(rows.findRetryTarget(id).cancellationId()).isNull();
    assertThat(rows.findRetryTarget(id).sourceId()).isEqualTo(returnId);
    when(provider.isConfigured()).thenReturn(true);
    when(provider.refund(anyString(), any())).thenReturn(new TossRefundAdapter.RefundResult("SUCCEEDED", "DONE"));
    assertThat(refunds.process(id).amount()).isEqualByComparingTo("12.34");
    assertCompleted(o, id, "RETURN");
    assertThat(f.text("SELECT status FROM order_returns WHERE id=?", returnId)).isEqualTo("COMPLETED");
    assertThat(f.integer("SELECT COUNT(*) FROM inventory_movements WHERE return_id=?", returnId)).isEqualTo(2);
    assertThat(f.integer("SELECT available_quantity FROM inventories WHERE sku_id=?", o.firstSku())).isEqualTo(12);
  }

  @Test
  void generatedSuccessKeyRejectsSecondSuccessfulAttemptAndMissingIdsKeep404() {
    OrderFixture o = cancelled(BigDecimal.ONE);
    long first = refund(o);
    jdbc.update("UPDATE refunds SET status='FAILED' WHERE id=?", first);
    long second = refunds.retry(first).refundId();
    jdbc.update("UPDATE refunds SET status='SUCCEEDED' WHERE id=?", first);
    assertThat(f.id("SELECT succeeded_order_id FROM refunds WHERE id=?", first)).isEqualTo(o.order());
    assertThat(jdbc.queryForObject("SELECT succeeded_order_id FROM refunds WHERE id=?", Long.class, second)).isNull();
    assertThatThrownBy(() -> new TransactionTemplate(manager).executeWithoutResult(status ->
        jdbc.update("UPDATE refunds SET status='SUCCEEDED' WHERE id=?", second)))
        .isInstanceOf(org.springframework.dao.DuplicateKeyException.class);
    assertThat(f.text("SELECT status FROM refunds WHERE id=?", second)).isEqualTo("READY");
    assertError(() -> refunds.process(Long.MAX_VALUE), 404, "REFUND_NOT_FOUND");
    assertError(() -> refunds.retry(Long.MAX_VALUE), 404, "REFUND_NOT_FOUND");
  }

  @Test
  void cancellationHttpReplaysOriginalProjectionAndPreservesOwnershipAndValidation() throws Exception {
    OrderFixture o = f.paidOrder("PREPARING", new BigDecimal("123.45"));
    var mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    String url = "/api/orders/" + o.order() + "/cancellations";
    String first = mvc.perform(post(url).with(authentication(auth(o.member(), MemberRole.USER))).with(csrf())
        .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"HTTP cancellation\"}"))
        .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
    String replay = mvc.perform(post(url).with(authentication(auth(o.member(), MemberRole.USER))).with(csrf())
        .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"different\"}"))
        .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
    assertThat(replay).isEqualTo(first);
    assertThat(json.readTree(first)).isEqualTo(json.readTree(json.writeValueAsString(cancellations.request(o.member(), o.order(), "ignored"))));
    mvc.perform(post(url).with(authentication(auth(o.admin(), MemberRole.USER))).with(csrf())
        .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"other member\"}")).andExpect(status().isNotFound());
    mvc.perform(post(url).with(authentication(auth(o.member(), MemberRole.USER))).with(csrf())
        .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\" \"}")).andExpect(status().isBadRequest());
    assertThat(f.integer("SELECT COUNT(*) FROM refunds WHERE order_id=?", o.order())).isEqualTo(1);
  }

  @Test
  void returnAndRefundHttpPreserveNullableTimestampDecimalAndAdminStateContracts() throws Exception {
    OrderFixture o = f.paidOrder("DELIVERED", new BigDecimal("12.34"));
    var mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    String body = mvc.perform(post("/api/orders/" + o.order() + "/returns")
        .with(authentication(auth(o.member(), MemberRole.USER))).with(csrf())
        .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"HTTP return\"}"))
        .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
    var requested = returns.request(o.member(), o.order(), "ignored");
    assertThat(json.readTree(body)).isEqualTo(json.readTree(json.writeValueAsString(requested)));
    String url = "/api/admin/returns/" + requested.returnId();
    mvc.perform(post(url + "/approve").with(authentication(auth(o.member(), MemberRole.USER))).with(csrf())).andExpect(status().isForbidden());
    mvc.perform(post(url + "/approve").with(authentication(auth(o.admin(), MemberRole.ADMIN))).with(csrf())).andExpect(status().isOk());
    mvc.perform(post(url + "/receive").with(authentication(auth(o.admin(), MemberRole.ADMIN))).with(csrf())
        .contentType(MediaType.APPLICATION_JSON).content("{\"restock\":false}")).andExpect(status().isOk());
    long id = refund(o);
    when(provider.isConfigured()).thenReturn(true);
    when(provider.refund(anyString(), any())).thenReturn(new TossRefundAdapter.RefundResult("SUCCEEDED", "HTTP_DONE"));
    String refundUrl = "/api/admin/refunds/" + id + "/process";
    mvc.perform(post(refundUrl).with(authentication(auth(o.member(), MemberRole.USER))).with(csrf())).andExpect(status().isForbidden());
    String result = mvc.perform(post(refundUrl).with(authentication(auth(o.admin(), MemberRole.ADMIN))).with(csrf()))
        .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
    assertThat(json.readTree(result)).isEqualTo(json.readTree(json.writeValueAsString(rows.find(id))));
    assertThat(json.readTree(result).get("amount").decimalValue()).isEqualByComparingTo("12.34");
    mvc.perform(post(refundUrl).with(authentication(auth(o.admin(), MemberRole.ADMIN))).with(csrf())).andExpect(status().isConflict());
    mvc.perform(post("/api/admin/refunds/" + Long.MAX_VALUE + "/process")
        .with(authentication(auth(o.admin(), MemberRole.ADMIN))).with(csrf())).andExpect(status().isNotFound());
    assertThat(f.integer("SELECT available_quantity FROM inventories WHERE sku_id=?", o.firstSku())).isEqualTo(10);
  }

  private UsernamePasswordAuthenticationToken auth(long member, MemberRole role) {
    return new UsernamePasswordAuthenticationToken(new AuthenticatedMemberPrincipal(member, role), null,
        List.of(new SimpleGrantedAuthority("ROLE_" + role.name())));
  }

  private void assertCompleted(OrderFixture o, long id, String source) {
    assertThat(f.text("SELECT status FROM refunds WHERE id=?", id)).isEqualTo("SUCCEEDED");
    assertThat(f.id("SELECT succeeded_order_id FROM refunds WHERE id=?", id)).isEqualTo(o.order());
    assertThat(jdbc.queryForObject("SELECT completed_at FROM refunds WHERE id=?", Timestamp.class, id)).isNotNull();
    assertThat(f.text("SELECT status FROM member_coupons WHERE id=?", o.coupon())).isEqualTo("AVAILABLE");
    assertThat(jdbc.queryForObject("SELECT reserved_order_id FROM member_coupons WHERE id=?", Long.class, o.coupon())).isNull();
    assertThat(jdbc.queryForObject("SELECT used_at FROM member_coupons WHERE id=?", Timestamp.class, o.coupon())).isNull();
    assertThat(f.integer("SELECT COUNT(*) FROM member_memberships WHERE member_id=?", o.member())).isEqualTo(1);
    assertThat(f.integer("SELECT COUNT(*) FROM membership_histories WHERE member_id=?", o.member())).isEqualTo(1);
    assertThat(f.integer("SELECT COUNT(*) FROM notifications WHERE member_id=? AND reference_type='REFUND' AND reference_id=? AND type=?", o.member(), id, source + "_COMPLETED")).isEqualTo(1);
    if ("CANCELLATION".equals(source)) assertThat(f.text("SELECT status FROM order_cancellations WHERE order_id=?", o.order())).isEqualTo("COMPLETED");
  }
  private void assertError(Runnable call, int status, String code) {
    assertThatThrownBy(call::run).isInstanceOfSatisfying(CommerceException.class, e -> {
      assertThat(e.status()).isEqualTo(status);
      assertThat(e.code()).isEqualTo(code);
    });
  }
}
