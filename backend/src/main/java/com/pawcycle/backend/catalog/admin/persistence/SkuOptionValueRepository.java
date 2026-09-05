package com.pawcycle.backend.catalog.admin.persistence;

import com.pawcycle.backend.catalog.admin.domain.SkuOptionValueEntity;
import com.pawcycle.backend.catalog.admin.domain.SkuOptionValueId;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SkuOptionValueRepository extends JpaRepository<SkuOptionValueEntity, SkuOptionValueId> {
  List<SkuOptionValueEntity> findBySku_Product_IdAndSku_IdNot(Long productId, Long skuId);

  @Query(
      "select sov.id.optionValueId from SkuOptionValueEntity sov "
          + "where sov.sku.id = :skuId order by sov.optionValue.optionGroup.displayOrder, "
          + "sov.optionValue.optionGroup.id, sov.optionValue.displayOrder, sov.optionValue.id")
  List<Long> findOptionValueIds(@Param("skuId") Long skuId);

  @Modifying
  @Query("delete from SkuOptionValueEntity sov where sov.sku.id = :skuId")
  int deleteAllBySkuId(@Param("skuId") Long skuId);
}
