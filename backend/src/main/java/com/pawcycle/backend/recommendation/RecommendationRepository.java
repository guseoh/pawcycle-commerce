package com.pawcycle.backend.recommendation;

import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
class RecommendationRepository {
	private final JdbcTemplate jdbc;

	RecommendationRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

	String findOwnedPetType(long memberId, long petId) {
		return jdbc.query("SELECT pet_type FROM pets WHERE id=? AND member_id=?",
				rs -> rs.next() ? rs.getString(1) : null, petId, memberId);
	}

	List<RecommendationCandidate> findPurchasableCandidates(String petType) {
		return jdbc.query("""
				SELECT product.id,product.name,product.short_description,product.thumbnail_url,product.pet_type,
				       category.id,category.name,category.slug
				FROM products product JOIN categories category ON category.id=product.category_id
				WHERE product.pet_type=? AND product.display_status='PUBLIC' AND category.active=true
				  AND EXISTS (
				    SELECT 1 FROM skus sku JOIN inventories inventory ON inventory.sku_id=sku.id
				    WHERE sku.product_id=product.id AND sku.status='ACTIVE' AND inventory.available_quantity>0
				  )
				ORDER BY product.id
				""", (rs, row) -> new RecommendationCandidate(
					rs.getLong(1), rs.getString(2), rs.getString(3), rs.getString(4), rs.getString(5),
					new RecommendationCandidate.Category(rs.getLong(6), rs.getString(7), rs.getString(8))), petType);
	}

	List<String> subscriptionCategorySlugs(long memberId, long petId) {
		return categories("""
				SELECT category.slug FROM subscriptions subscription
				JOIN subscription_snapshots snapshot ON snapshot.id=subscription.current_snapshot_id
				JOIN subscription_snapshot_items item ON item.snapshot_id=snapshot.id
				JOIN skus sku ON sku.id=item.sku_id JOIN products product ON product.id=sku.product_id
				JOIN categories category ON category.id=product.category_id
				WHERE subscription.member_id=? AND subscription.pet_id=? AND subscription.mvp2_managed=true AND subscription.status='ACTIVE'
				GROUP BY category.id,category.slug ORDER BY COUNT(*) DESC,category.id
				""", memberId, petId);
	}

	List<String> purchaseCategorySlugs(long memberId) {
		return categories("""
				SELECT category.slug FROM orders orders JOIN payments payment ON payment.order_id=orders.id AND payment.status='SUCCEEDED'
				JOIN order_items item ON item.order_id=orders.id JOIN skus sku ON sku.id=item.sku_id
				JOIN products product ON product.id=sku.product_id JOIN categories category ON category.id=product.category_id
				WHERE orders.member_id=? AND orders.status='PAID'
				GROUP BY category.id,category.slug ORDER BY COUNT(*) DESC,category.id
				""", memberId);
	}

	List<String> wishlistCategorySlugs(long memberId) {
		return categories("""
				SELECT category.slug FROM wishlist_items wishlist JOIN products product ON product.id=wishlist.product_id
				JOIN categories category ON category.id=product.category_id WHERE wishlist.member_id=?
				GROUP BY category.id,category.slug ORDER BY COUNT(*) DESC,category.id
				""", memberId);
	}

	private List<String> categories(String sql, Object... args) { return jdbc.queryForList(sql, String.class, args); }
}
