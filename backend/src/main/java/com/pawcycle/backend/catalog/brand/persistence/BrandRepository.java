package com.pawcycle.backend.catalog.brand.persistence;

import com.pawcycle.backend.catalog.brand.domain.Brand;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface BrandRepository extends JpaRepository<Brand, Long> {
  boolean existsBySlug(String slug);

  boolean existsBySlugAndIdNot(String slug, Long id);

  List<Brand> findAllByOrderByDisplayOrderAscIdAsc();

  List<Brand> findAllByActiveTrueOrderByDisplayOrderAscIdAsc();

  @Query(
      "select case when count(p) > 0 then true else false end from Product p "
          + "join Brand b on b.id = p.brandId where p.id = :productId and b.active = true")
  boolean hasActiveBrandForProduct(@Param("productId") Long productId);
}
