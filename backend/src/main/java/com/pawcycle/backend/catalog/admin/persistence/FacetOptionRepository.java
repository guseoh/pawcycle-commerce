package com.pawcycle.backend.catalog.admin.persistence;

import com.pawcycle.backend.catalog.admin.domain.FacetOptionEntity;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FacetOptionRepository extends JpaRepository<FacetOptionEntity, Long> {
  List<FacetOptionEntity> findByFacetDefinition_IdOrderByDisplayOrderAscIdAsc(Long definitionId);

  Optional<FacetOptionEntity> findByFacetDefinition_IdAndId(Long definitionId, Long optionId);
}
