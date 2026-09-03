package com.pawcycle.backend.commerce.inventory.application;

import com.pawcycle.backend.commerce.AdminAuditService;
import com.pawcycle.backend.commerce.CommerceException;
import com.pawcycle.backend.commerce.InventoryService;
import com.pawcycle.backend.commerce.inventory.api.InventoryResponse;
import com.pawcycle.backend.commerce.inventory.persistence.InventoryRepository;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class InventoryAdminApplicationService {
  private final InventoryRepository inventories;
  private final AdminAuditService audits;
  private final InventoryService inventory;

  public InventoryAdminApplicationService(
      InventoryRepository inventories, AdminAuditService audits, InventoryService inventory) {
    this.inventories = inventories;
    this.audits = audits;
    this.inventory = inventory;
  }

  @Transactional
  public void adjust(long adminId, long skuId, int delta) {
    if (delta == 0) {
      throw new CommerceException(400, "INVENTORY_ADJUSTMENT_INVALID", "재고 조정 수량은 0일 수 없습니다.");
    }
    inventory.adjust(skuId, delta);
    audits.append(adminId, "INVENTORY_ADJUST", "SKU", skuId);
  }

  @Transactional(readOnly = true)
  public List<InventoryResponse> list() {
    return inventories.findAdminProjections().stream()
        .map(
            row ->
                new InventoryResponse(
                    row.getSkuId(),
                    row.getAvailableQuantity(),
                    row.getReservedQuantity(),
                    row.getVersion(),
                    row.getSkuCode()))
        .toList();
  }
}