package com.pawcycle.backend.commerce.wishlist.application;

import com.pawcycle.backend.commerce.CommerceException;
import com.pawcycle.backend.commerce.CommercePayload;
import com.pawcycle.backend.foundation.persistence.NativeQueryExecutor;
import java.sql.Timestamp;
import java.time.Clock;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class WishlistApplicationService {
  private final NativeQueryExecutor jdbc;
  private final Clock clock;

  public WishlistApplicationService(NativeQueryExecutor jdbc, Clock clock) {
    this.jdbc = jdbc;
    this.clock = clock;
  }

  @Transactional(readOnly = true)
  public CommercePayload list(long memberId) {
    return CommercePayload.from(
        Map.of(
            "items",
            jdbc.queryForList(
                """
                SELECT item.product_id AS productId,product.name AS productName,item.created_at AS createdAt
                FROM wishlist_items item JOIN products product ON product.id=item.product_id
                WHERE item.member_id=? ORDER BY item.created_at DESC,item.product_id DESC\
                """,
                memberId)));
  }

  @Transactional
  public void add(long memberId, long productId) {
    if (jdbc.queryForObject("SELECT COUNT(*) FROM products WHERE id=?", Integer.class, productId)
        == 0) {
      throw new CommerceException(404, "PRODUCT_NOT_FOUND", "요청한 리소스를 찾을 수 없습니다.");
    }
    jdbc.update(
        "INSERT INTO wishlist_items(member_id,product_id,created_at) VALUES (?,?,?)"
            + " ON DUPLICATE KEY UPDATE product_id=VALUES(product_id)",
        memberId,
        productId,
        Timestamp.from(clock.instant()));
  }

  @Transactional
  public void remove(long memberId, long productId) {
    jdbc.update(
        "DELETE FROM wishlist_items WHERE member_id=? AND product_id=?", memberId, productId);
  }
}