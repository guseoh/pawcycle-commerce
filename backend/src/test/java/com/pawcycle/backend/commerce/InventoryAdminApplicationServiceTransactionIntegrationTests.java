package com.pawcycle.backend.commerce;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;

import com.pawcycle.backend.commerce.inventory.application.InventoryAdminApplicationService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

@SpringBootTest
@ActiveProfiles("test")
class InventoryAdminApplicationServiceTransactionIntegrationTests {
  private static final String SKU_CODE = "TXN-ROLLBACK-ADMIN";

  @Autowired private InventoryAdminApplicationService service;
  @Autowired private JdbcTemplate jdbc;
  @MockitoSpyBean private AdminAuditService audits;

  private long categoryId;
  private long skuId;
  private long productId;

  @BeforeEach
  void setUp() {
    jdbc.update(
        "INSERT INTO categories(name,slug,display_order,active) VALUES"
            + " (?,'txn-rollback-admin',0,false)",
        "rollback-test-category");
    categoryId = jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
    jdbc.update(
        "INSERT INTO products(brand_id,category_id,name,short_description,pet_type,display_status)"
            + " VALUES (1,?,?,'rollback test','DOG','PUBLIC')",
        categoryId,
        "rollback-test-product");
    productId = jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
    jdbc.update(
        "INSERT INTO skus(product_id,sku_code,name,price,subscribable,display_order,status) VALUES"
            + " (?,?,'rollback test',100,true,1,'ACTIVE')",
        productId,
        SKU_CODE);
    skuId = jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
    jdbc.update(
        "INSERT INTO inventories(sku_id,available_quantity,reserved_quantity,version) VALUES"
            + " (?,10,0,0)",
        skuId);
  }

  @AfterEach
  void tearDown() {
    jdbc.update("DELETE FROM inventory_movements WHERE sku_id=?", skuId);
    jdbc.update("DELETE FROM inventories WHERE sku_id=?", skuId);
    jdbc.update("DELETE FROM skus WHERE id=?", skuId);
    jdbc.update("DELETE FROM products WHERE id=?", productId);
    jdbc.update("DELETE FROM categories WHERE id=?", categoryId);
  }

  @Test
  void auditFailureRollsBackInventoryMutation() {
    doThrow(new IllegalStateException("audit failure"))
        .when(audits)
        .append(1L, "INVENTORY_ADJUST", "SKU", skuId);

    assertThatThrownBy(() -> service.adjust(1L, skuId, 5))
        .isInstanceOf(IllegalStateException.class);

    assertThat(
            jdbc.queryForObject(
                "SELECT available_quantity FROM inventories WHERE sku_id=?", Integer.class, skuId))
        .isEqualTo(10);
    assertThat(
            jdbc.queryForObject(
                "SELECT COUNT(*) FROM inventory_movements WHERE sku_id=?", Integer.class, skuId))
        .isZero();
  }
}
