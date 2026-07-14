package com.pawcycle.backend.subscription.domain;

import com.pawcycle.backend.catalog.sku.domain.Sku;
import com.pawcycle.backend.member.domain.Member;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.util.Set;

@Entity
@Table(name = "subscriptions")
public class Subscription {

	private static final Set<Integer> ALLOWED_DELIVERY_CYCLES = Set.of(2, 4, 8);

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "member_id", nullable = false)
	private Member member;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "sku_id", nullable = false)
	private Sku sku;

	@Column(nullable = false)
	private int quantity;

	@Column(name = "delivery_cycle_weeks", nullable = false)
	private int deliveryCycleWeeks;

	@Column(name = "created_date", nullable = false)
	private LocalDate createdDate;

	@Column(name = "next_order_date", nullable = false)
	private LocalDate nextOrderDate;

	protected Subscription() {
	}

	private Subscription(
			Member member,
			Sku sku,
			int quantity,
			int deliveryCycleWeeks,
			LocalDate createdDate,
			LocalDate nextOrderDate) {
		if (member == null || sku == null) {
			throw new IllegalArgumentException("구독 소유자와 SKU는 필수입니다.");
		}
		if (quantity < 1 || quantity > 10) {
			throw new IllegalArgumentException("구독 수량은 1개 이상 10개 이하여야 합니다.");
		}
		if (!ALLOWED_DELIVERY_CYCLES.contains(deliveryCycleWeeks)) {
			throw new IllegalArgumentException("배송 주기는 2주, 4주 또는 8주여야 합니다.");
		}
		if (createdDate == null || nextOrderDate == null
				|| !nextOrderDate.equals(createdDate.plusWeeks(deliveryCycleWeeks))) {
			throw new IllegalArgumentException("다음 주문 예정일이 배송 주기와 일치하지 않습니다.");
		}
		this.member = member;
		this.sku = sku;
		this.quantity = quantity;
		this.deliveryCycleWeeks = deliveryCycleWeeks;
		this.createdDate = createdDate;
		this.nextOrderDate = nextOrderDate;
	}

	public static Subscription create(
			Member member,
			Sku sku,
			int quantity,
			int deliveryCycleWeeks,
			LocalDate createdDate) {
		if (createdDate == null) {
			throw new IllegalArgumentException("구독 생성일은 필수입니다.");
		}
		return new Subscription(
				member,
				sku,
				quantity,
				deliveryCycleWeeks,
				createdDate,
				createdDate.plusWeeks(deliveryCycleWeeks));
	}

	public Long getId() {
		return id;
	}

	public Member getMember() {
		return member;
	}

	public Sku getSku() {
		return sku;
	}

	public int getQuantity() {
		return quantity;
	}

	public int getDeliveryCycleWeeks() {
		return deliveryCycleWeeks;
	}

	public LocalDate getCreatedDate() {
		return createdDate;
	}

	public LocalDate getNextOrderDate() {
		return nextOrderDate;
	}
}
