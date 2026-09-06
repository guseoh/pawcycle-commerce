package com.pawcycle.backend.catalog.admin.persistence;

import com.pawcycle.backend.catalog.admin.domain.FacetDefinitionEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FacetDefinitionRepository extends JpaRepository<FacetDefinitionEntity, Long> {
  List<FacetDefinitionEntity> findAllByOrderByIdAsc();
}
