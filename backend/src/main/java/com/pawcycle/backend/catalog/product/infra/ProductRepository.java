package com.pawcycle.backend.catalog.product.infra;

import com.pawcycle.backend.catalog.product.domain.Product;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProductRepository extends JpaRepository<Product, Long> {

	List<Product> findAllByName(String name);

	@Query("SELECT p FROM Product p LEFT JOIN FETCH p.category ORDER BY p.id ASC")
	List<Product> findAllWithCategoryOrderByIdAsc();

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("SELECT p FROM Product p WHERE p.id = :productId")
	Optional<Product> findByIdForUpdate(@Param("productId") Long productId);

	@Query("""
			SELECT p
			FROM Product p
			LEFT JOIN FETCH p.category
			WHERE p.status = com.pawcycle.backend.catalog.product.domain.ProductStatus.PUBLIC
			ORDER BY p.id ASC
			""")
	List<Product> findAllPublicOrderById();

	@Query("""
			SELECT p
			FROM Product p
			LEFT JOIN FETCH p.category
			WHERE p.id = :productId
			  AND p.status = com.pawcycle.backend.catalog.product.domain.ProductStatus.PUBLIC
			""")
	Optional<Product> findPublicById(@Param("productId") Long productId);
}
