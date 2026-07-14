package com.pawcycle.backend.catalog.product.infra;

import com.pawcycle.backend.catalog.product.domain.Product;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProductRepository extends JpaRepository<Product, Long> {

	List<Product> findAllByName(String name);

	@Query(value = """
			SELECT p.*
			FROM products p
			WHERE BINARY p.display_status = 'PUBLIC'
			ORDER BY p.id ASC
			""", nativeQuery = true)
	List<Product> findAllPublicOrderById();

	@Query(value = """
			SELECT p.*
			FROM products p
			WHERE p.id = :productId
			  AND BINARY p.display_status = 'PUBLIC'
			""", nativeQuery = true)
	Optional<Product> findPublicById(@Param("productId") Long productId);
}
