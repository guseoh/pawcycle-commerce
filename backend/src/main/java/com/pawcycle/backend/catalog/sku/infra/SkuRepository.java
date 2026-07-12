package com.pawcycle.backend.catalog.sku.infra;

import com.pawcycle.backend.catalog.sku.domain.Sku;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SkuRepository extends JpaRepository<Sku, Long> {

	List<Sku> findAllByProductIdOrderByDisplayOrderAscIdAsc(Long productId);

	List<Sku> findAllByProductIdInOrderByProductIdAscDisplayOrderAscIdAsc(Collection<Long> productIds);
}
