package com.pawcycle.backend.commerce;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderItemRepository extends JpaRepository<CommerceOrderItemEntity, Long> {
  List<CommerceOrderItemEntity> findAllByOrderId(long orderId);
}
