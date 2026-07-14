package com.pawcycle.backend.subscription.application;

import com.pawcycle.backend.catalog.product.domain.Product;
import com.pawcycle.backend.catalog.sku.domain.Sku;
import com.pawcycle.backend.catalog.sku.infra.SkuRepository;
import com.pawcycle.backend.member.domain.Member;
import com.pawcycle.backend.member.infra.MemberRepository;
import com.pawcycle.backend.subscription.application.SubscriptionDetailView.ProductSummary;
import com.pawcycle.backend.subscription.application.SubscriptionDetailView.SkuSummary;
import com.pawcycle.backend.subscription.application.SubscriptionListView.SubscriptionSummary;
import com.pawcycle.backend.subscription.domain.Subscription;
import com.pawcycle.backend.subscription.infra.SubscriptionRepository;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SubscriptionApplicationService {

	private static final ZoneId SEOUL_ZONE = ZoneId.of("Asia/Seoul");

	private final SubscriptionRepository subscriptionRepository;
	private final MemberRepository memberRepository;
	private final SkuRepository skuRepository;
	private final Clock clock;

	public SubscriptionApplicationService(
			SubscriptionRepository subscriptionRepository,
			MemberRepository memberRepository,
			SkuRepository skuRepository,
			Clock clock) {
		this.subscriptionRepository = subscriptionRepository;
		this.memberRepository = memberRepository;
		this.skuRepository = skuRepository;
		this.clock = clock;
	}

	@Transactional
	public SubscriptionCreateResult create(
			Long memberId,
			Long skuId,
			int quantity,
			int deliveryCycleWeeks) {
		try {
			Sku sku = skuRepository.findById(skuId).orElseThrow(SkuNotFoundException::new);
			if (!sku.isSubscribable()) {
				throw new SkuNotSubscribableException();
			}
			Member member = memberRepository.getReferenceById(memberId);
			LocalDate createdDate = LocalDate.now(clock.withZone(SEOUL_ZONE));
			Subscription subscription = subscriptionRepository.save(Subscription.create(
					member, sku, quantity, deliveryCycleWeeks, createdDate));
			return new SubscriptionCreateResult(subscription.getId(), subscription.getNextOrderDate());
		} catch (SkuNotFoundException | SkuNotSubscribableException exception) {
			throw exception;
		} catch (RuntimeException exception) {
			throw new SubscriptionCreateFailedException(exception);
		}
	}

	@Transactional(readOnly = true)
	public SubscriptionListView findOwnedSubscriptions(Long memberId) {
		try {
			List<SubscriptionSummary> subscriptions = subscriptionRepository
					.findAllOwnedWithCatalogOrderByIdDesc(memberId)
					.stream()
					.map(this::toSummary)
					.toList();
			return new SubscriptionListView(subscriptions);
		} catch (RuntimeException exception) {
			throw new SubscriptionListUnavailableException(exception);
		}
	}

	@Transactional(readOnly = true)
	public SubscriptionDetailView findOwnedSubscription(Long memberId, Long subscriptionId) {
		try {
			return subscriptionRepository.findOwnedWithCatalog(subscriptionId, memberId)
					.map(this::toDetail)
					.orElseThrow(SubscriptionNotFoundException::new);
		} catch (SubscriptionNotFoundException exception) {
			throw exception;
		} catch (RuntimeException exception) {
			throw new SubscriptionDetailUnavailableException(exception);
		}
	}

	private SubscriptionSummary toSummary(Subscription subscription) {
		Sku sku = subscription.getSku();
		Product product = sku.getProduct();
		return new SubscriptionSummary(
				subscription.getId(),
				new SubscriptionListView.ProductSummary(product.getId(), product.getName()),
				new SubscriptionListView.SkuSummary(sku.getId(), sku.getName()),
				subscription.getQuantity(),
				subscription.getDeliveryCycleWeeks(),
				subscription.getNextOrderDate());
	}

	private SubscriptionDetailView toDetail(Subscription subscription) {
		Sku sku = subscription.getSku();
		Product product = sku.getProduct();
		return new SubscriptionDetailView(
				subscription.getId(),
				new ProductSummary(product.getId(), product.getName()),
				new SkuSummary(sku.getId(), sku.getName(), sku.getPrice()),
				subscription.getQuantity(),
				subscription.getDeliveryCycleWeeks(),
				subscription.getCreatedDate(),
				subscription.getNextOrderDate());
	}
}
