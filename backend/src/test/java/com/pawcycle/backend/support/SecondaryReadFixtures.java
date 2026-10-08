package com.pawcycle.backend.support;

import com.pawcycle.backend.catalog.brand.domain.Brand;
import com.pawcycle.backend.catalog.category.domain.Category;
import com.pawcycle.backend.catalog.product.domain.Product;
import com.pawcycle.backend.catalog.sku.domain.Sku;
import com.pawcycle.backend.catalog.sku.domain.SkuStatus;
import com.pawcycle.backend.member.domain.Member;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;

/** Disposable, transaction-scoped fixtures shared by T05 JDBC/JPA parity tests. */
public class SecondaryReadFixtures {
  private final JdbcTemplate jdbc;
  private final EntityManager entities;

  public SecondaryReadFixtures(JdbcTemplate jdbc, EntityManager entities) {
    this.jdbc = jdbc;
    this.entities = entities;
  }

  public static Timestamp stamp() { return Timestamp.valueOf("2026-09-18 12:34:56.123456"); }
  public static String unique() { return "T05-" + UUID.randomUUID(); }

  public long member() {
    var member = new Member(unique() + "@example.test", "unused-fixture");
    entities.persist(member);
    entities.flush();
    return member.getId();
  }

  public Category category(boolean active) {
    var category = new Category("T05 category", unique(), 0, active);
    entities.persist(category);
    return category;
  }

  public Brand brand(boolean active) {
    var brand = new Brand("T05 brand", unique(), null, active, 0);
    entities.persist(brand);
    return brand;
  }

  public Product product(Category category, Brand brand, String petType, String status) {
    var product = new Product(category, "T05 product", "fixture description", null, petType, null, status);
    product.updateBrandId(brand.getId());
    entities.persist(product);
    return product;
  }

  public Sku sku(Product product, SkuStatus status, Integer inventory) {
    var sku = new Sku(product, unique(), "T05 SKU", new BigDecimal("123.45"), false, 0, status);
    entities.persist(sku);
    entities.flush();
    if (inventory != null) jdbc.update("INSERT INTO inventories(sku_id,available_quantity,reserved_quantity,version) VALUES (?,?,0,0)", sku.getId(), inventory);
    return sku;
  }

  public long order(long memberId, String source, String status, BigDecimal amount, Timestamp paidAt) {
    jdbc.update("""
        INSERT INTO orders(order_number,member_id,source,status,original_amount,discount_amount,shipping_fee,
          payment_amount,recipient_name,recipient_phone,postal_code,address_line1,created_at,paid_at)
        VALUES (?,?,?,?,?,0,0,?,'Fixture recipient','fixture-phone','fixture-postal','Fixture address',?,?)
        """, unique(), memberId, source, status, amount, amount, stamp(), paidAt);
    return lastId();
  }

  public long payment(long orderId, String type, String status, int attempt) {
    jdbc.update("""
        INSERT INTO payments(order_id,type,provider,status,amount,provider_order_id,idempotency_key,
          attempt_no,requested_at,created_at) VALUES (?,?,'TOSS',?,123.45,?,?,?,?,?)
        """, orderId, type, status, unique(), unique(), attempt, stamp(), stamp());
    return lastId();
  }

  public void item(long orderId, Sku sku) {
    jdbc.update("INSERT INTO order_items(order_id,sku_id,snapshot_quality,quantity) VALUES (?,?,'LEGACY_PARTIAL',1)", orderId, sku.getId());
  }

  public long cancellation(long orderId) {
    jdbc.update("INSERT INTO order_cancellations(order_id,status,reason,requested_at) VALUES (?,'COMPLETED','fixture',?)", orderId, stamp());
    return lastId();
  }

  public long refund(long orderId, long cancellationId, String status, int attempt) {
    jdbc.update("""
        INSERT INTO refunds(order_id,source,cancellation_id,status,amount,provider,idempotency_key,
          attempt_no,requested_at,processed_at) VALUES (?,'CANCELLATION',?,?,123.45,'TOSS',?,?,?,?)
        """, orderId, cancellationId, status, unique(), attempt, stamp(), stamp());
    return lastId();
  }

  public long lastId() { return jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class); }
}
