package com.pawcycle.backend.commerce.cart.persistence;

import com.pawcycle.backend.foundation.persistence.NativeQueryExecutor;
import java.util.List;
import org.springframework.stereotype.Repository;

@Repository
public class CartQueryRepository {
  private final NativeQueryExecutor queries;

  public CartQueryRepository(NativeQueryExecutor queries) {
    this.queries = queries;
  }

  public CartView find(long memberId) {
    List<CartItemView> items =
        queries.query(
            """
            SELECT item.sku_id AS skuId,item.quantity,sku.sku_code AS skuCode,sku.name AS skuName,sku.price,sku.price AS unitPrice,
                   sku.price * item.quantity AS lineAmount,product.id AS productId,product.name AS productName,
                   inventory.available_quantity AS availableQuantity,
                   (sku.status='ACTIVE' AND product.display_status='PUBLIC' AND category.active=true AND inventory.available_quantity >= item.quantity) AS purchasable
            FROM carts cart JOIN cart_items item ON item.cart_id=cart.id
            JOIN skus sku ON sku.id=item.sku_id JOIN products product ON product.id=sku.product_id
            JOIN categories category ON category.id=product.category_id
            JOIN inventories inventory ON inventory.sku_id=sku.id
            WHERE cart.member_id=? ORDER BY item.sku_id
            """,
            (rs, rowNumber) ->
                new CartItemView(
                    rs.getLong("skuId"),
                    rs.getInt("quantity"),
                    rs.getString("skuCode"),
                    rs.getString("skuName"),
                    rs.getBigDecimal("price"),
                    rs.getBigDecimal("unitPrice"),
                    rs.getBigDecimal("lineAmount"),
                    rs.getLong("productId"),
                    rs.getString("productName"),
                    rs.getInt("availableQuantity"),
                    rs.getBoolean("purchasable")),
            memberId);
    List<Long> versions = queries.queryForList("SELECT version FROM carts WHERE member_id=?", Long.class, memberId);
    return new CartView(items, versions.isEmpty() ? 0L : versions.getFirst());
  }

  public boolean isPurchasable(long skuId) {
    List<Integer> matches =
        queries.queryForList(
            """
            SELECT COUNT(*) FROM skus sku JOIN products product ON product.id=sku.product_id
            JOIN categories category ON category.id=product.category_id
            WHERE sku.id=? AND sku.status='ACTIVE' AND product.display_status='PUBLIC'
              AND category.active=true
            """,
            Integer.class,
            skuId);
    return !matches.isEmpty() && matches.getFirst() == 1;
  }
}
