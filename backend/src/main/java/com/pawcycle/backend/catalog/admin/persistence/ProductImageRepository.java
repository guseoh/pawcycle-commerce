package com.pawcycle.backend.catalog.admin.persistence;

import com.pawcycle.backend.catalog.admin.domain.ProductImageEntity;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductImageRepository extends JpaRepository<ProductImageEntity, Long> {
  List<ProductImageEntity> findByProduct_IdOrderByDisplayOrderAscIdAsc(Long productId);

  Optional<ProductImageEntity> findByProduct_IdAndId(Long productId, Long imageId);
}
