package com.pawcycle.backend.catalog.sku.persistence;

import com.pawcycle.backend.catalog.sku.domain.Sku;
import com.pawcycle.backend.catalog.sku.domain.SkuStatus;
import jakarta.persistence.LockModeType;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SkuRepository extends JpaRepository<Sku, Long> {

  List<Sku> findAllByProductIdAndName(Long productId, String name);

  List<Sku> findAllByProductIdOrderByDisplayOrderAscIdAsc(Long productId);

  List<Sku> findAllByProductIdInOrderByProductIdAscDisplayOrderAscIdAsc(
      Collection<Long> productIds);

  List<Sku> findAllByProductIdAndStatusOrderByDisplayOrderAscIdAsc(
      Long productId, SkuStatus status);

  List<Sku> findAllByProductIdInAndStatusOrderByProductIdAscDisplayOrderAscIdAsc(
      Collection<Long> productIds, SkuStatus status);

  Optional<Sku> findByIdAndProductId(Long id, Long productId);

  boolean existsBySkuCode(String skuCode);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select sku from Sku sku join fetch sku.product where sku.id in :skuIds")
  List<Sku> findAllByIdInForUpdate(@Param("skuIds") Collection<Long> skuIds);
}
