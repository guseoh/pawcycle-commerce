package com.pawcycle.backend.catalog.admin.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.pawcycle.backend.catalog.admin.model.CatalogAdminCommands.ImageCreateCommand;
import com.pawcycle.backend.catalog.admin.model.CatalogAdminCommands.ImagePatchCommand;
import com.pawcycle.backend.catalog.product.application.ProductListCache;
import com.pawcycle.backend.commerce.audit.application.AdminAuditService;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@SpringBootTest
@ActiveProfiles("test")
class CatalogAdminCommandTransactionIntegrationTests {
  @Autowired private AdminCatalogMutationService mutations;
  @Autowired private JdbcTemplate jdbc;
  @MockitoSpyBean private ProductListCache cache;
  @MockitoSpyBean private AdminAuditService audits;
  private long productId;
  private long categoryId;

  @BeforeEach
  void setUp() {
    jdbc.update("INSERT IGNORE INTO members(id,email,password_hash,role) VALUES"
        + " (1,'catalog-command-admin@example.test','fixture','ADMIN')");
    jdbc.update("INSERT INTO categories(name,slug,display_order,active)"
        + " VALUES ('catalog-command-boundary','catalog-command-boundary',0,true)");
    categoryId = jdbc.queryForObject("SELECT id FROM categories WHERE slug='catalog-command-boundary'", Long.class);
    jdbc.update("INSERT INTO products(brand_id,category_id,name,short_description,pet_type,display_status)"
        + " VALUES (1,?,'catalog-command-boundary','fixture','DOG','PUBLIC')", categoryId);
    productId = jdbc.queryForObject("SELECT id FROM products WHERE name='catalog-command-boundary'", Long.class);
  }

  @AfterEach
  void cleanUp() {
    jdbc.update("DELETE FROM admin_audit_logs WHERE target_type='PRODUCT_IMAGE' AND target_id IN"
        + " (SELECT id FROM product_images WHERE product_id=?)", productId);
    jdbc.update("DELETE FROM product_images WHERE product_id=?", productId);
    jdbc.update("DELETE FROM products WHERE id=?", productId);
    jdbc.update("DELETE FROM categories WHERE id=?", categoryId);
  }

  @Test
  void generatedIdAndNullablePatchKeepAuditReferenceAndCommitInvalidation() {
    var created = mutations.createImage(1L, productId, new ImageCreateCommand("https://example.test/image", "alt", 3, "DETAIL"));
    assertThat(created.imageId()).isPositive();
    assertThat(jdbc.queryForObject("SELECT target_id FROM admin_audit_logs WHERE action='CATALOG_PRODUCT_IMAGE_CREATE'"
        + " AND target_type='PRODUCT_IMAGE' AND target_id=?", Long.class, created.imageId())).isEqualTo(created.imageId());
    clearInvocations(cache);
    var updated = mutations.updateImage(1L, productId, created.imageId(),
        new ImagePatchCommand(null, false, null, true, null, false, null, false));
    assertThat(updated.altText()).isNull();
    assertThat(updated.imageUrl()).isEqualTo(created.imageUrl());
    assertThat(updated.displayOrder()).isEqualTo(3);
    verify(cache).invalidate();
    assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM admin_audit_logs WHERE action='CATALOG_PRODUCT_IMAGE_UPDATE'"
        + " AND target_type='PRODUCT_IMAGE' AND target_id=?", Integer.class, created.imageId())).isEqualTo(1);
  }

  @Test
  void failureAfterAuditFlushRollsBackGeneratedImageAuditAndCacheCallback() {
    clearInvocations(cache);
    AtomicLong generated = new AtomicLong();
    doAnswer(invocation -> {
      assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isTrue();
      long id = invocation.getArgument(3);
      generated.set(id);
      assertThat(jdbc.queryForObject("SELECT image_url FROM product_images WHERE id=?", String.class, id))
          .isEqualTo("https://example.test/rollback");
      invocation.callRealMethod();
      assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM admin_audit_logs WHERE action=? AND target_id=?",
          Integer.class, invocation.getArgument(1), id)).isEqualTo(1);
      throw new IllegalStateException("catalog audit rollback probe");
    }).when(audits).append(anyLong(), anyString(), anyString(), anyLong());
    assertThatThrownBy(() -> mutations.createImage(1L, productId,
        new ImageCreateCommand("https://example.test/rollback", null, 0, "DETAIL")))
        .isInstanceOf(IllegalStateException.class).hasMessage("catalog audit rollback probe");
    assertThat(generated.get()).isPositive();
    assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM product_images WHERE id=?", Integer.class, generated.get())).isZero();
    assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM admin_audit_logs WHERE action='CATALOG_PRODUCT_IMAGE_CREATE'"
        + " AND target_id=?", Integer.class, generated.get())).isZero();
    verify(cache, never()).invalidate();
  }
}
