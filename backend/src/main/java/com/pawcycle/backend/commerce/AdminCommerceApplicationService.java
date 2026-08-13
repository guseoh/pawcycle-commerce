package com.pawcycle.backend.commerce;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class AdminCommerceApplicationService {
    private final CommerceService commerce;
    private final AdminAuditService audits;

    AdminCommerceApplicationService(CommerceService commerce, AdminAuditService audits) {
        this.commerce = commerce;
        this.audits = audits;
    }

    @Transactional
    void adjust(long adminId, long skuId, int delta) {
        commerce.adjustInventory(skuId, delta);
        audits.append(adminId, "INVENTORY_ADJUST", "SKU", skuId);
    }
}
