package com.pawcycle.backend.catalog.sku.infra;

import com.pawcycle.backend.catalog.sku.domain.Sku;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SkuRepository extends JpaRepository<Sku, Long> {

	List<Sku> findAllByProductIdOrderByDisplayOrderAscIdAsc(Long productId);

	@Query("""
			SELECT s
			FROM Sku s
			JOIN FETCH s.product
			WHERE s.product.id IN :productIds
			ORDER BY s.product.id ASC, s.displayOrder ASC, s.id ASC
			""")
	List<Sku> findAllByProductIdInOrderByProductIdAscDisplayOrderAscIdAsc(
			@Param("productIds") Collection<Long> productIds);
}
