package com.pawcycle.backend.commerce.wishlist.persistence;

import com.pawcycle.backend.foundation.persistence.NativeQueryExecutor;
import java.util.List;
import org.springframework.stereotype.Repository;

@Repository
public class WishlistQueryRepository {
  private final NativeQueryExecutor queries;

  public WishlistQueryRepository(NativeQueryExecutor queries) {
    this.queries = queries;
  }

  public List<WishlistItemView> findByMemberId(long memberId) {
    return queries.query(
        """
        SELECT item.product_id AS productId,product.name AS productName,item.created_at AS createdAt
        FROM wishlist_items item JOIN products product ON product.id=item.product_id
        WHERE item.member_id=? ORDER BY item.created_at DESC,item.product_id DESC
        """,
        (rs, rowNumber) ->
            new WishlistItemView(
                rs.getLong("productId"), rs.getString("productName"), rs.getTimestamp("createdAt")),
        memberId);
  }
}
