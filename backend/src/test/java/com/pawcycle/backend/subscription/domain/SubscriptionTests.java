package com.pawcycle.backend.subscription.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import com.pawcycle.backend.catalog.sku.domain.Sku;
import com.pawcycle.backend.member.domain.Member;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class SubscriptionTests {

	private final Member member = mock(Member.class);
	private final Sku sku = mock(Sku.class);
	private final LocalDate createdDate = LocalDate.of(2026, 7, 14);

	@Test
	void createsNextOrderDateByAddingDeliveryCycleWithoutCalendarAdjustment() {
		Subscription subscription = Subscription.create(member, sku, 2, 4, createdDate);

		assertThat(subscription.getMember()).isSameAs(member);
		assertThat(subscription.getSku()).isSameAs(sku);
		assertThat(subscription.getQuantity()).isEqualTo(2);
		assertThat(subscription.getDeliveryCycleWeeks()).isEqualTo(4);
		assertThat(subscription.getCreatedDate()).isEqualTo(createdDate);
		assertThat(subscription.getNextOrderDate()).isEqualTo(LocalDate.of(2026, 8, 11));
	}

	@Test
	void rejectsQuantityOutsideApprovedRange() {
		assertThatThrownBy(() -> Subscription.create(member, sku, 0, 4, createdDate))
				.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> Subscription.create(member, sku, 11, 4, createdDate))
				.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void acceptsOnlyApprovedDeliveryCycles() {
		for (int cycle : new int[] {2, 4, 8}) {
			assertThat(Subscription.create(member, sku, 1, cycle, createdDate).getNextOrderDate())
					.isEqualTo(createdDate.plusWeeks(cycle));
		}
		assertThatThrownBy(() -> Subscription.create(member, sku, 1, 6, createdDate))
				.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void rejectsMissingOwnerSkuAndCreatedDate() {
		assertThatThrownBy(() -> Subscription.create(null, sku, 1, 2, createdDate))
				.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> Subscription.create(member, null, 1, 2, createdDate))
				.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> Subscription.create(member, sku, 1, 2, null))
				.isInstanceOf(IllegalArgumentException.class);
	}
}
