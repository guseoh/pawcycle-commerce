package com.pawcycle.backend.commerce.cart.persistence;

import com.pawcycle.backend.catalog.product.domain.ProductStatus;
import com.pawcycle.backend.catalog.sku.domain.SkuStatus;
import com.pawcycle.backend.commerce.CartEntity;
import com.pawcycle.backend.commerce.CartRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import java.util.List;
import org.springframework.stereotype.Repository;

@Repository
public class CartQueryRepository {
  private final EntityManager entityManager;
  private final CartRepository carts;

  public CartQueryRepository(EntityManager entityManager, CartRepository carts) {
    this.entityManager = entityManager;
    this.carts = carts;
  }

  public CartView find(long memberId) {
    TypedQuery<CartItemRow> query =
        entityManager.createQuery(
            """
            select new com.pawcycle.backend.commerce.cart.persistence.CartItemRow(
                item.id.skuId, item.quantity, sku.skuCode, sku.name, sku.price, sku.price,
                sku.price * item.quantity, product.id, product.name,
                coalesce(inventory.availableQuantity, 0),
                case when sku.status = :skuStatus
                    and product.status = :productStatus
                    and category.active = true
                    and coalesce(inventory.availableQuantity, 0) >= item.quantity
                  then true else false end)
            from CartItemEntity item
            join item.cart cart
            join Sku sku on sku.id = item.id.skuId
            join sku.product product
            join product.category category
            left join InventoryEntity inventory on inventory.skuId = sku.id
            where cart.memberId = :memberId
            order by item.id.skuId
            """,
            CartItemRow.class);
    query.setParameter("memberId", memberId);
    query.setParameter("skuStatus", SkuStatus.ACTIVE);
    query.setParameter("productStatus", ProductStatus.PUBLIC);
    List<CartItemView> items = query.getResultList().stream().map(CartItemRow::toView).toList();
    long version = carts.findByMemberId(memberId).map(CartEntity::getVersion).orElse(0L);
    return new CartView(items, version);
  }

  public boolean isPurchasable(long skuId) {
    Long matches =
        entityManager
            .createQuery(
                """
                select count(sku)
                from Sku sku
                join sku.product product
                join product.category category
                where sku.id = :skuId
                  and sku.status = :skuStatus
                  and product.status = :productStatus
                  and category.active = true
                """,
                Long.class)
            .setParameter("skuId", skuId)
            .setParameter("skuStatus", SkuStatus.ACTIVE)
            .setParameter("productStatus", ProductStatus.PUBLIC)
            .getSingleResult();
    return matches == 1L;
  }
}
