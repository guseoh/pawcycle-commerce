package com.pawcycle.backend.catalog.sku.infra;

import com.pawcycle.backend.catalog.sku.domain.Sku;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SkuRepository extends JpaRepository<Sku, Long> {

	List<Sku> findAllByProductIdAndName(Long productId, String name);

	List<Sku> findAllByProductIdOrderByDisplayOrderAscIdAsc(Long productId);

	List<Sku> findAllByProductIdInOrderByProductIdAscDisplayOrderAscIdAsc(Collection<Long> productIds);

	List<Sku> findAllByProductIdAndStatusOrderByDisplayOrderAscIdAsc(
			Long productId,
			com.pawcycle.backend.catalog.sku.domain.SkuStatus status);

	List<Sku> findAllByProductIdInAndStatusOrderByProductIdAscDisplayOrderAscIdAsc(
			Collection<Long> productIds,
			com.pawcycle.backend.catalog.sku.domain.SkuStatus status);

	Optional<Sku> findByIdAndProductId(Long id, Long productId);

	boolean existsBySkuCode(String skuCode);
}
