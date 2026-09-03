package com.pawcycle.backend.commerce.inventory.persistence;

import com.pawcycle.backend.catalog.sku.domain.Sku;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "inventories")
public class InventoryEntity {
  @Id
  @Column(name = "sku_id")
  private Long skuId;

  @OneToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "sku_id", insertable = false, updatable = false)
  private Sku sku;

  @Column(name = "available_quantity", nullable = false)
  private int availableQuantity;

  @Column(name = "reserved_quantity", nullable = false)
  private int reservedQuantity;

  @Column(nullable = false)
  private long version;

  protected InventoryEntity() {}

  public long skuId() {
    return skuId;
  }

  public int availableQuantity() {
    return availableQuantity;
  }

  public int reservedQuantity() {
    return reservedQuantity;
  }

  public long version() {
    return version;
  }

  public void release(int quantity) {
    availableQuantity += quantity;
    reservedQuantity -= quantity;
    version++;
  }

  public void deduct(int quantity) {
    reservedQuantity -= quantity;
    version++;
  }

  public void restore(int quantity) {
    availableQuantity += quantity;
    version++;
  }

  public void adjust(int delta) {
    availableQuantity += delta;
    version++;
  }
}