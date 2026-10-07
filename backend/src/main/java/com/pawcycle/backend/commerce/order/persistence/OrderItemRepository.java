package com.pawcycle.backend.commerce.order.persistence;

import com.pawcycle.backend.commerce.order.domain.CommerceOrderItemEntity;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderItemRepository extends JpaRepository<CommerceOrderItemEntity, Long> {
  List<CommerceOrderItemEntity> findAllByOrderId(long orderId);
}
