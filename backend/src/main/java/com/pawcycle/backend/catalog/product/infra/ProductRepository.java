package com.pawcycle.backend.catalog.product.infra;

import com.pawcycle.backend.catalog.product.domain.Product;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductRepository extends JpaRepository<Product, Long> {
}
