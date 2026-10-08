package com.pawcycle.backend.commerce.inventory.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.pawcycle.backend.catalog.sku.domain.SkuStatus;
import com.pawcycle.backend.commerce.common.error.CommerceException;
import com.pawcycle.backend.commerce.inventory.application.InventoryService;
import com.pawcycle.backend.support.SecondaryReadFixtures;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** Regression protection for the existing JPA implementation; no production Inventory rewrite. */
@SpringBootTest
@ActiveProfiles({"test", "local-integration"})
class InventoryProtocolIntegrationTests {
  @Autowired private InventoryService inventory;
  @Autowired private InventoryRepository inventories;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private EntityManager entities;
  @Autowired private PlatformTransactionManager manager;

  @Test
  void reserveDeductReleaseRestoreKeepVersionAndMovementInCallerTransaction() {
    Fixture f = fixture();
    new TransactionTemplate(manager).executeWithoutResult(status -> {
      inventory.reserve(f.sku(), 3, f.payment());
      inventory.deduct(f.sku(), 2, f.payment());
      inventory.release(f.sku(), 1, f.payment());
      inventory.restoreCancellation(f.sku(), 2, f.cancellation());
    });
    assertThat(integer("SELECT available_quantity FROM inventories WHERE sku_id=?", f.sku())).isEqualTo(10);
    assertThat(integer("SELECT reserved_quantity FROM inventories WHERE sku_id=?", f.sku())).isZero();
    assertThat(integer("SELECT version FROM inventories WHERE sku_id=?", f.sku())).isEqualTo(4);
    assertThat(jdbc.queryForList("SELECT type FROM inventory_movements WHERE sku_id=? ORDER BY id", String.class, f.sku()))
        .containsExactly("RESERVE", "DEDUCT", "RELEASE", "CANCEL_RESTORE");
    Integer staleVersionResult = new TransactionTemplate(manager).execute(status -> inventories.reserveIfVersionMatches(f.sku(), 1, 0));
    assertThat(staleVersionResult).isZero();
    assertThat(integer("SELECT available_quantity FROM inventories WHERE sku_id=?", f.sku())).isEqualTo(10);
    assertThat(integer("SELECT COUNT(*) FROM inventory_movements WHERE sku_id=?", f.sku())).isEqualTo(4);
  }

  @Test
  void callerFailureRollsBackCasAndAuditAndMissingInventoryKeepsErrors() {
    Fixture f = fixture();
    assertThatThrownBy(() -> new TransactionTemplate(manager).executeWithoutResult(status -> {
      inventory.reserve(f.sku(), 2, f.payment());
      throw new IllegalStateException("caller failure");
    })).isInstanceOf(IllegalStateException.class);
    assertThat(integer("SELECT available_quantity FROM inventories WHERE sku_id=?", f.sku())).isEqualTo(10);
    assertThat(integer("SELECT reserved_quantity FROM inventories WHERE sku_id=?", f.sku())).isZero();
    assertThat(integer("SELECT version FROM inventories WHERE sku_id=?", f.sku())).isZero();
    assertThat(integer("SELECT COUNT(*) FROM inventory_movements WHERE sku_id=?", f.sku())).isZero();
    assertThatThrownBy(() -> new TransactionTemplate(manager).executeWithoutResult(status -> inventory.reserve(Long.MAX_VALUE, 1, f.payment())))
        .isInstanceOfSatisfying(CommerceException.class, e -> assertThat(e.code()).isEqualTo("INVENTORY_INSUFFICIENT"));
    assertThatThrownBy(() -> new TransactionTemplate(manager).executeWithoutResult(status -> inventory.adjust(Long.MAX_VALUE, 1)))
        .isInstanceOfSatisfying(CommerceException.class, e -> assertThat(e.code()).isEqualTo("INVENTORY_NOT_FOUND"));
  }

  private Fixture fixture() {
    return new TransactionTemplate(manager).execute(status -> {
      var fixtures = new SecondaryReadFixtures(jdbc, entities);
      long member = fixtures.member();
      var product = fixtures.product(fixtures.category(false), fixtures.brand(true), "DOG", "PUBLIC");
      var sku = fixtures.sku(product, SkuStatus.ACTIVE, 10);
      long order = fixtures.order(member, "ONE_TIME", "PAYMENT_PENDING", BigDecimal.ONE, null);
      long payment = fixtures.payment(order, "NORMAL", "READY", 1);
      return new Fixture(sku.getId(), payment, fixtures.cancellation(order));
    });
  }
  private int integer(String sql, Object... args) { return jdbc.queryForObject(sql, Integer.class, args); }
  private record Fixture(long sku, long payment, long cancellation) {}
}
