package com.pawcycle.backend.catalog.admin.persistence;

import com.pawcycle.backend.catalog.admin.domain.ProductOptionGroupEntity;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductOptionGroupRepository extends JpaRepository<ProductOptionGroupEntity, Long> {
  long countByProduct_Id(Long productId);

  List<ProductOptionGroupEntity> findByProduct_IdOrderByDisplayOrderAscIdAsc(Long productId);

  Optional<ProductOptionGroupEntity> findByProduct_IdAndId(Long productId, Long groupId);
}
