package com.pawcycle.backend.commerce.wishlist.persistence;

import jakarta.persistence.EntityManager;
import java.util.List;
import org.springframework.stereotype.Repository;

@Repository
public class WishlistQueryRepository {
  private final EntityManager entityManager;

  public WishlistQueryRepository(EntityManager entityManager) {
    this.entityManager = entityManager;
  }

  public List<WishlistItemView> findByMemberId(long memberId) {
    return entityManager
        .createQuery(
            """
            select new com.pawcycle.backend.commerce.wishlist.persistence.WishlistItemRow(
                item.id.productId, product.name, item.createdAt)
            from WishlistItemEntity item
            join Product product on product.id = item.id.productId
            where item.id.memberId = :memberId
            order by item.createdAt desc, item.id.productId desc
            """,
            WishlistItemRow.class)
        .setParameter("memberId", memberId)
        .getResultList()
        .stream()
        .map(WishlistItemRow::toView)
        .toList();
  }
}
