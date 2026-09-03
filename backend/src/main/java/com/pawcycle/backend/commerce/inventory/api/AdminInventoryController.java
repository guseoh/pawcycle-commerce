package com.pawcycle.backend.commerce.inventory.api;

import com.pawcycle.backend.commerce.InventoryAdjustmentRequest;
import com.pawcycle.backend.commerce.inventory.application.InventoryAdminApplicationService;
import com.pawcycle.backend.member.application.AuthenticatedMemberPrincipal;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/inventories")
public class AdminInventoryController {
  private final InventoryAdminApplicationService inventory;

  public AdminInventoryController(InventoryAdminApplicationService inventory) {
    this.inventory = inventory;
  }

  @GetMapping
  public List<InventoryResponse> inventories() {
    return inventory.list();
  }

  @PostMapping("/{skuId}/adjustments")
  public ResponseEntity<Void> adjust(
      @AuthenticationPrincipal AuthenticatedMemberPrincipal principal,
      @PathVariable long skuId,
      @Valid @RequestBody InventoryAdjustmentRequest request) {
    inventory.adjust(principal.memberId(), skuId, request.delta());
    return ResponseEntity.noContent().build();
  }
}