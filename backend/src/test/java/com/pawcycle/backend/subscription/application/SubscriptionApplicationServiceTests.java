package com.pawcycle.backend.subscription.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.pawcycle.backend.catalog.product.domain.Product;
import com.pawcycle.backend.catalog.sku.domain.Sku;
import com.pawcycle.backend.catalog.sku.infra.SkuRepository;
import com.pawcycle.backend.member.domain.Member;
import com.pawcycle.backend.member.infra.MemberRepository;
import com.pawcycle.backend.subscription.domain.Subscription;
import com.pawcycle.backend.subscription.infra.SubscriptionRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.annotation.Transactional;

@ExtendWith(MockitoExtension.class)
class SubscriptionApplicationServiceTests {

	@Mock
	private SubscriptionRepository subscriptionRepository;
	@Mock
	private MemberRepository memberRepository;
	@Mock
	private SkuRepository skuRepository;

	private SubscriptionApplicationService service;

	@BeforeEach
	void setUp() {
		Clock clock = Clock.fixed(Instant.parse("2026-07-13T15:30:00Z"), ZoneOffset.UTC);
		service = new SubscriptionApplicationService(
				subscriptionRepository, memberRepository, skuRepository, clock);
	}

	@Test
	void createUsesPrincipalOwnerSeoulDateAndPersistsOneSubscription() {
		Member member = mock(Member.class);
		Sku sku = mock(Sku.class);
		Subscription saved = mock(Subscription.class);
		when(skuRepository.findById(10L)).thenReturn(Optional.of(sku));
		when(sku.isSubscribable()).thenReturn(true);
		when(memberRepository.getReferenceById(20L)).thenReturn(member);
		when(saved.getId()).thenReturn(501L);
		when(saved.getNextOrderDate()).thenReturn(LocalDate.of(2026, 8, 11));
		when(subscriptionRepository.save(any(Subscription.class))).thenReturn(saved);

		SubscriptionCreateResult result = service.create(20L, 10L, 2, 4);

		assertThat(result).isEqualTo(new SubscriptionCreateResult(501L, LocalDate.of(2026, 8, 11)));
		ArgumentCaptor<Subscription> captor = ArgumentCaptor.forClass(Subscription.class);
		verify(subscriptionRepository).save(captor.capture());
		assertThat(captor.getValue().getMember()).isSameAs(member);
		assertThat(captor.getValue().getSku()).isSameAs(sku);
		assertThat(captor.getValue().getCreatedDate()).isEqualTo(LocalDate.of(2026, 7, 14));
		assertThat(captor.getValue().getNextOrderDate()).isEqualTo(LocalDate.of(2026, 8, 11));
	}

	@Test
	void missingAndNotSubscribableSkuDoNotPersist() {
		when(skuRepository.findById(99L)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> service.create(20L, 99L, 1, 2))
				.isInstanceOf(SkuNotFoundException.class);
		verifyNoInteractions(memberRepository, subscriptionRepository);

		Sku sku = mock(Sku.class);
		when(skuRepository.findById(10L)).thenReturn(Optional.of(sku));
		when(sku.isSubscribable()).thenReturn(false);
		assertThatThrownBy(() -> service.create(20L, 10L, 1, 2))
				.isInstanceOf(SkuNotSubscribableException.class);
		verify(memberRepository, never()).getReferenceById(any());
		verify(subscriptionRepository, never()).save(any());
	}

	@Test
	void listAndDetailMapFetchedCatalogWithoutExposingEntities() {
		Subscription newer = subscription(502L, "새 상품", "4kg", "29000.00", 2, 8);
		Subscription older = subscription(501L, "기존 상품", "2kg", "19000.00", 1, 4);
		when(subscriptionRepository.findAllOwnedWithCatalogOrderByIdDesc(20L))
				.thenReturn(List.of(newer, older));
		when(subscriptionRepository.findOwnedWithCatalog(502L, 20L)).thenReturn(Optional.of(newer));

		SubscriptionListView list = service.findOwnedSubscriptions(20L);
		SubscriptionDetailView detail = service.findOwnedSubscription(20L, 502L);

		assertThat(list.subscriptions()).extracting(SubscriptionListView.SubscriptionSummary::subscriptionId)
				.containsExactly(502L, 501L);
		assertThat(list.subscriptions().get(0).product().name()).isEqualTo("새 상품");
		assertThat(list.subscriptions().get(0).sku().skuName()).isEqualTo("4kg");
		assertThat(detail.sku().price()).isEqualByComparingTo("29000.00");
		assertThat(detail.createdDate()).isEqualTo(LocalDate.of(2026, 7, 14));
	}

	@Test
	void emptyListAndUnavailableDetailUseApprovedResults() {
		when(subscriptionRepository.findAllOwnedWithCatalogOrderByIdDesc(20L)).thenReturn(List.of());
		when(subscriptionRepository.findOwnedWithCatalog(999L, 20L)).thenReturn(Optional.empty());

		assertThat(service.findOwnedSubscriptions(20L).subscriptions()).isEmpty();
		assertThatThrownBy(() -> service.findOwnedSubscription(20L, 999L))
				.isInstanceOf(SubscriptionNotFoundException.class);
	}

	@Test
	void unexpectedFailuresUseEndpointSpecificExceptions() {
		when(skuRepository.findById(10L)).thenThrow(new IllegalStateException("create storage"));
		when(subscriptionRepository.findAllOwnedWithCatalogOrderByIdDesc(20L))
				.thenThrow(new IllegalStateException("list storage"));
		when(subscriptionRepository.findOwnedWithCatalog(1L, 20L))
				.thenThrow(new IllegalStateException("detail storage"));

		assertThatThrownBy(() -> service.create(20L, 10L, 1, 2))
				.isInstanceOf(SubscriptionCreateFailedException.class);
		assertThatThrownBy(() -> service.findOwnedSubscriptions(20L))
				.isInstanceOf(SubscriptionListUnavailableException.class);
		assertThatThrownBy(() -> service.findOwnedSubscription(20L, 1L))
				.isInstanceOf(SubscriptionDetailUnavailableException.class);
	}

	@Test
	void methodsDeclareApprovedTransactionBoundaries() throws NoSuchMethodException {
		Transactional create = SubscriptionApplicationService.class
				.getMethod("create", Long.class, Long.class, int.class, int.class)
				.getAnnotation(Transactional.class);
		Transactional list = SubscriptionApplicationService.class
				.getMethod("findOwnedSubscriptions", Long.class)
				.getAnnotation(Transactional.class);
		Transactional detail = SubscriptionApplicationService.class
				.getMethod("findOwnedSubscription", Long.class, Long.class)
				.getAnnotation(Transactional.class);

		assertThat(create.readOnly()).isFalse();
		assertThat(list.readOnly()).isTrue();
		assertThat(detail.readOnly()).isTrue();
	}

	private Subscription subscription(
			Long id,
			String productName,
			String skuName,
			String price,
			int quantity,
			int cycle) {
		Product product = mock(Product.class);
		when(product.getId()).thenReturn(id + 1000);
		when(product.getName()).thenReturn(productName);
		Sku sku = mock(Sku.class);
		when(sku.getId()).thenReturn(id + 2000);
		when(sku.getProduct()).thenReturn(product);
		when(sku.getName()).thenReturn(skuName);
		lenient().when(sku.getPrice()).thenReturn(new BigDecimal(price));
		Subscription subscription = mock(Subscription.class);
		when(subscription.getId()).thenReturn(id);
		when(subscription.getSku()).thenReturn(sku);
		when(subscription.getQuantity()).thenReturn(quantity);
		when(subscription.getDeliveryCycleWeeks()).thenReturn(cycle);
		lenient().when(subscription.getCreatedDate()).thenReturn(LocalDate.of(2026, 7, 14));
		when(subscription.getNextOrderDate()).thenReturn(LocalDate.of(2026, 7, 14).plusWeeks(cycle));
		return subscription;
	}
}
