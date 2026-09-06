package com.pawcycle.backend.catalog.admin.persistence;

import com.pawcycle.backend.catalog.admin.domain.ProductOptionValueEntity;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductOptionValueRepository extends JpaRepository<ProductOptionValueEntity, Long> {
  List<ProductOptionValueEntity> findByOptionGroup_IdOrderByDisplayOrderAscIdAsc(Long groupId);

  Optional<ProductOptionValueEntity> findByOptionGroup_IdAndId(Long groupId, Long valueId);

  List<ProductOptionValueEntity> findByOptionGroup_Product_IdAndIdIn(Long productId, List<Long> ids);
}
