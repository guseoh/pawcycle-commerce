package com.pawcycle.backend.catalog.brand.infra;

import com.pawcycle.backend.catalog.brand.domain.Brand;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BrandRepository extends JpaRepository<Brand, Long> {
    boolean existsBySlug(String slug);
    boolean existsBySlugAndIdNot(String slug, Long id);
    List<Brand> findAllByOrderByDisplayOrderAscIdAsc();
}
