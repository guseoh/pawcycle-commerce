package com.pawcycle.backend.subscription.infra;

import com.pawcycle.backend.subscription.domain.Subscription;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SubscriptionRepository extends JpaRepository<Subscription, Long> {

	@Modifying(flushAutomatically = true, clearAutomatically = true)
	@Query("DELETE FROM Subscription subscription WHERE subscription.member.id = :memberId")
	int deleteAllByMemberId(@Param("memberId") Long memberId);

	@Query("""
			SELECT subscription
			FROM Subscription subscription
			JOIN FETCH subscription.sku sku
			JOIN FETCH sku.product product
			WHERE subscription.member.id = :memberId
			ORDER BY subscription.id DESC
			""")
	List<Subscription> findAllOwnedWithCatalogOrderByIdDesc(@Param("memberId") Long memberId);

	@Query("""
			SELECT subscription
			FROM Subscription subscription
			JOIN FETCH subscription.sku sku
			JOIN FETCH sku.product product
			WHERE subscription.id = :subscriptionId
			  AND subscription.member.id = :memberId
			""")
	Optional<Subscription> findOwnedWithCatalog(
			@Param("subscriptionId") Long subscriptionId,
			@Param("memberId") Long memberId);
}
