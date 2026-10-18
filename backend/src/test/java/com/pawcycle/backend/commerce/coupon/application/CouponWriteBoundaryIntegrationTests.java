package com.pawcycle.backend.commerce.coupon.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.pawcycle.backend.commerce.coupon.api.CouponRequest;
import com.pawcycle.backend.commerce.coupon.persistence.CouponRepository;
import com.pawcycle.backend.commerce.coupon.persistence.MemberCouponRepository;
import com.pawcycle.backend.commerce.coupon.persistence.FrozenCouponPersistenceReference;
import com.pawcycle.backend.member.persistence.MemberRepository;
import com.pawcycle.backend.support.SecondaryReadFixtures;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.Clock;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest
@ActiveProfiles("test")
class CouponWriteBoundaryIntegrationTests {
  @Autowired CouponAdminApplicationService service;
  @Autowired JdbcTemplate jdbc;
  @Autowired EntityManager entities;
  @Autowired PlatformTransactionManager manager;
  @Autowired CouponRepository coupons;
  @Autowired MemberCouponRepository memberCoupons;
  @Autowired MemberRepository members;
  @Autowired Clock clock;
  private final String prefix = "coupon-boundary-" + UUID.randomUUID();
  private long admin;

  @BeforeEach
  void member() {
    admin = new TransactionTemplate(manager).execute(s -> new SecondaryReadFixtures(jdbc, entities).member());
  }

  @AfterEach
  void clean() {
    jdbc.update("DELETE FROM admin_audit_logs WHERE admin_id=?", admin);
    jdbc.update("DELETE FROM coupons WHERE name LIKE ?", prefix + "%");
    jdbc.update("DELETE FROM members WHERE id=?", admin);
  }

  @Test
  void generatedIdAuditDecimalNullAndSecondTruncationRemainTheDatabaseContract() {
    long id = service.create(admin, command(prefix, "123.456", null));
    assertThat(id).isPositive();
    assertThat(jdbc.queryForObject("SELECT target_id FROM admin_audit_logs WHERE admin_id=? AND action='COUPON_CREATE'", Long.class, admin)).isEqualTo(id);
    assertThat(jdbc.queryForObject("SELECT discount_value FROM coupons WHERE id=?", BigDecimal.class, id)).isEqualByComparingTo("123.46");
    assertThat(jdbc.queryForObject("SELECT maximum_discount_amount FROM coupons WHERE id=?", BigDecimal.class, id)).isNull();
    long legacy = new TransactionTemplate(manager).execute(s -> frozen().create(request(prefix + "-legacy", "123.456", null)));
    assertThat(values(id)).isEqualTo(values(legacy));
    assertThat(jdbc.queryForObject("SELECT MICROSECOND(valid_from)+MICROSECOND(valid_until) FROM coupons WHERE id=?", Integer.class, id)).isZero();
    long max = service.create(admin, command(prefix + "-max", "9999999999.99", null));
    assertThat(jdbc.queryForObject("SELECT discount_value FROM coupons WHERE id=?", BigDecimal.class, max)).isEqualByComparingTo("9999999999.99");
  }

  @Test
  void lockedPatchPreservesAbsentFieldsAndClearsExplicitNull() {
    long id = service.create(admin, command(prefix, "10", new BigDecimal("500")));
    long legacy = new TransactionTemplate(manager).execute(s -> frozen().create(request(prefix + "-legacy", "10", new BigDecimal("500"))));
    new TransactionTemplate(manager).executeWithoutResult(s -> {
      var current = frozen().findForUpdate(legacy);
      frozen().update(legacy, new CouponRequest(prefix + "-legacy-patched", current.discountType(),
          current.discountValue(), current.minimumOrderAmount(), null,
          LocalDateTime.ofInstant(current.validFrom().toInstant(), ZoneOffset.UTC),
          LocalDateTime.ofInstant(current.validUntil().toInstant(), ZoneOffset.UTC), current.active()));
    });
    service.update(admin, id, patch(prefix + "-renamed"));
    assertThat(jdbc.queryForObject("SELECT name FROM coupons WHERE id=?", String.class, id)).isEqualTo(prefix + "-renamed");
    assertThat(jdbc.queryForObject("SELECT maximum_discount_amount FROM coupons WHERE id=?", BigDecimal.class, id)).isNull();
    assertThat(jdbc.queryForObject("SELECT discount_value FROM coupons WHERE id=?", BigDecimal.class, id)).isEqualByComparingTo("10");
    assertThat(values(id)).isEqualTo(values(legacy));
    assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM admin_audit_logs WHERE admin_id=? AND action='COUPON_UPDATE' AND target_id=?", Integer.class, admin, id)).isOne();
  }

  @Test
  void auditForeignKeyFailureRollsBackAlreadyFlushedCreateAndUpdate() {
    assertThatThrownBy(() -> service.create(Long.MAX_VALUE, command(prefix + "-failed", "10", null))).isInstanceOf(RuntimeException.class);
    assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM coupons WHERE name=?", Integer.class, prefix + "-failed")).isZero();
    long id = service.create(admin, command(prefix, "10", new BigDecimal("500")));
    assertThatThrownBy(() -> service.update(Long.MAX_VALUE, id, patch(prefix + "-failed-update"))).isInstanceOf(RuntimeException.class);
    assertThat(jdbc.queryForObject("SELECT name FROM coupons WHERE id=?", String.class, id)).isEqualTo(prefix);
    assertThat(jdbc.queryForObject("SELECT maximum_discount_amount FROM coupons WHERE id=?", BigDecimal.class, id)).isEqualByComparingTo("500");
    assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM admin_audit_logs WHERE target_type='COUPON' AND target_id=?", Integer.class, id)).isOne();
  }

  @Test
  void databasePrecisionAndMissingMemberFailureDoNotAppendAudit() {
    assertThatThrownBy(() -> service.create(admin, command(prefix + "-overflow", "10000000000.00", null))).isInstanceOf(RuntimeException.class);
    assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM coupons WHERE name=?", Integer.class, prefix + "-overflow")).isZero();
    long id = service.create(admin, command(prefix, "10", null));
    assertThatThrownBy(() -> service.issue(admin, id, Long.MAX_VALUE)).isInstanceOf(com.pawcycle.backend.commerce.common.error.CommerceException.class);
    assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM member_coupons WHERE coupon_id=?", Integer.class, id)).isZero();
    assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM admin_audit_logs WHERE admin_id=?", Integer.class, admin)).isOne();
  }

  private CouponCreateCommand command(String name, String amount, BigDecimal maximum) {
    var request = request(name, amount, maximum);
    return new CouponCreateCommand(request.name(), request.discountType(), request.discountValue(),
        request.minimumOrderAmount(), request.maximumDiscountAmount(), request.validFrom(), request.validUntil(), request.active());
  }

  private CouponRequest request(String name, String amount, BigDecimal maximum) {
    return new CouponRequest(name, "FIXED_AMOUNT", new BigDecimal(amount), BigDecimal.ZERO, maximum,
        LocalDateTime.parse("2026-08-01T00:00:00.123456"), LocalDateTime.parse("2026-09-01T00:00:00.999999"), true);
  }

  private java.util.Map<String, Object> values(long id) {
    return jdbc.queryForMap("SELECT discount_type,discount_value,minimum_order_amount,maximum_discount_amount,active,DATE_FORMAT(valid_from,'%Y-%m-%d %H:%i:%s.%f') AS valid_from,DATE_FORMAT(valid_until,'%Y-%m-%d %H:%i:%s.%f') AS valid_until FROM coupons WHERE id=?", id);
  }

  private FrozenCouponPersistenceReference frozen() {
    return new FrozenCouponPersistenceReference(coupons, memberCoupons, members, clock);
  }

  private CouponPatchCommand patch(String name) {
    return new CouponPatchCommand(name, null, null, null, null, null, null, null,
        true, false, false, false, true, false, false, false);
  }
}
