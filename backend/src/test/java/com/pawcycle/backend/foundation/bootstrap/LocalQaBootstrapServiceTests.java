package com.pawcycle.backend.foundation.bootstrap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.pawcycle.backend.catalog.product.domain.Product;
import com.pawcycle.backend.catalog.product.infra.ProductRepository;
import com.pawcycle.backend.catalog.sku.domain.Sku;
import com.pawcycle.backend.catalog.sku.infra.SkuRepository;
import com.pawcycle.backend.member.application.AuthValidationException;
import com.pawcycle.backend.member.application.EmailNormalizer;
import com.pawcycle.backend.member.domain.Member;
import com.pawcycle.backend.member.infra.MemberRepository;
import com.pawcycle.backend.subscription.infra.SubscriptionRepository;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

class LocalQaBootstrapServiceTests {

	private EmailNormalizer emailNormalizer;
	private PasswordEncoder passwordEncoder;
	private MemberRepository memberRepository;
	private ProductRepository productRepository;
	private SkuRepository skuRepository;
	private SubscriptionRepository subscriptionRepository;
	private LocalQaBootstrapService bootstrapService;

	@BeforeEach
	void setUp() {
		emailNormalizer = new EmailNormalizer();
		passwordEncoder = mock(PasswordEncoder.class);
		memberRepository = mock(MemberRepository.class);
		productRepository = mock(ProductRepository.class);
		skuRepository = mock(SkuRepository.class);
		subscriptionRepository = mock(SubscriptionRepository.class);
		bootstrapService = new LocalQaBootstrapService(
				emailNormalizer,
				passwordEncoder,
				memberRepository,
				productRepository,
				skuRepository,
				subscriptionRepository);
	}

	@Test
	void missingOrInvalidCredentialsFailBeforePersistenceWithoutLeakingValues() {
		String email = "not-the-reserved-local-part@" + UUID.randomUUID() + ".example";
		String password = UUID.randomUUID().toString();

		assertThatThrownBy(() -> bootstrapService.bootstrap(email, password, false))
				.isInstanceOf(LocalQaBootstrapException.class)
				.satisfies(error -> {
					assertThat(error.getMessage().contains(email)).isFalse();
					assertThat(error.getMessage().contains(password)).isFalse();
				});
		assertThatThrownBy(() -> bootstrapService.bootstrap(runtimeQaEmail(), null, false))
				.isInstanceOf(LocalQaBootstrapException.class);

		verifyNoInteractions(memberRepository, productRepository, skuRepository, subscriptionRepository);
	}

	@Test
	void invalidEmailPreservesGeneralizedValidationCauseWithoutLeakingInput() {
		String email = UUID.randomUUID() + "-invalid-email";
		String password = UUID.randomUUID().toString();

		assertThatThrownBy(() -> bootstrapService.bootstrap(email, password, false))
				.isInstanceOf(LocalQaBootstrapException.class)
				.hasMessage("로컬 QA bootstrap credential 설정이 없거나 유효하지 않습니다.")
				.hasCauseInstanceOf(AuthValidationException.class)
				.satisfies(error -> {
					assertThat(error.getMessage()).doesNotContain(email, password);
					assertThat(error.getCause().getMessage()).doesNotContain(email, password);
				});
	}

	@Test
	void firstRunCreatesRuntimeHashedMemberAndDeterministicCatalogWithoutReset() {
		String email = runtimeQaEmail();
		String password = UUID.randomUUID().toString();
		String hash = UUID.randomUUID().toString();
		when(memberRepository.findByEmailForUpdate(email)).thenReturn(Optional.empty());
		when(passwordEncoder.encode(password)).thenReturn(hash);
		when(memberRepository.saveAndFlush(any(Member.class))).thenAnswer(invocation -> invocation.getArgument(0));
		when(productRepository.findAllByName(LocalQaBootstrapService.PRODUCT_NAME)).thenReturn(List.of());
		when(productRepository.saveAndFlush(any(Product.class))).thenAnswer(invocation -> invocation.getArgument(0));
		when(skuRepository.findAllByProductIdAndName(null, LocalQaBootstrapService.SKU_NAME))
				.thenReturn(List.of());
		when(skuRepository.saveAndFlush(any(Sku.class))).thenAnswer(invocation -> invocation.getArgument(0));

		bootstrapService.bootstrap(email, password, false);

		verify(passwordEncoder).encode(password);
		verify(memberRepository).saveAndFlush(any(Member.class));
		verify(productRepository).saveAndFlush(any(Product.class));
		verify(skuRepository).saveAndFlush(any(Sku.class));
		verify(subscriptionRepository, never()).deleteAllByMemberId(any());
	}

	@Test
	void repeatedRunReusesExactFixtureAndDoesNotOverwriteMember() {
		String email = runtimeQaEmail();
		String password = UUID.randomUUID().toString();
		Member member = member(email);
		Product product = matchingProduct();
		Sku sku = matchingSku(product);
		when(memberRepository.findByEmailForUpdate(email)).thenReturn(Optional.of(member));
		when(passwordEncoder.matches(password, member.getPasswordHash())).thenReturn(true);
		when(productRepository.findAllByName(LocalQaBootstrapService.PRODUCT_NAME)).thenReturn(List.of(product));
		when(skuRepository.findAllByProductIdAndName(product.getId(), LocalQaBootstrapService.SKU_NAME))
				.thenReturn(List.of(sku));

		bootstrapService.bootstrap(email, password, false);

		verify(passwordEncoder, never()).encode(any());
		verify(memberRepository, never()).saveAndFlush(any());
		verify(productRepository, never()).saveAndFlush(any());
		verify(skuRepository, never()).saveAndFlush(any());
	}

	@Test
	void existingMemberPasswordMismatchFailsWithoutOverwrite() {
		String email = runtimeQaEmail();
		String password = UUID.randomUUID().toString();
		Member member = member(email);
		when(memberRepository.findByEmailForUpdate(email)).thenReturn(Optional.of(member));
		when(passwordEncoder.matches(password, member.getPasswordHash())).thenReturn(false);

		assertThatThrownBy(() -> bootstrapService.bootstrap(email, password, false))
				.isInstanceOf(LocalQaBootstrapException.class);

		verify(memberRepository, never()).saveAndFlush(any());
		verifyNoInteractions(productRepository, skuRepository, subscriptionRepository);
	}

	@Test
	void ambiguousProductOrSkuCandidatesFailInsteadOfSelectingOne() {
		String email = runtimeQaEmail();
		String password = UUID.randomUUID().toString();
		Member member = member(email);
		Product product = matchingProduct();
		when(memberRepository.findByEmailForUpdate(email)).thenReturn(Optional.of(member));
		when(passwordEncoder.matches(password, member.getPasswordHash())).thenReturn(true);
		when(productRepository.findAllByName(LocalQaBootstrapService.PRODUCT_NAME))
				.thenReturn(List.of(product, matchingProduct()));

		assertThatThrownBy(() -> bootstrapService.bootstrap(email, password, false))
				.isInstanceOf(LocalQaBootstrapException.class);

		when(productRepository.findAllByName(LocalQaBootstrapService.PRODUCT_NAME)).thenReturn(List.of(product));
		when(skuRepository.findAllByProductIdAndName(product.getId(), LocalQaBootstrapService.SKU_NAME))
				.thenReturn(List.of(matchingSku(product), matchingSku(product)));

		assertThatThrownBy(() -> bootstrapService.bootstrap(email, password, false))
				.isInstanceOf(LocalQaBootstrapException.class);
	}

	@Test
	void singleMismatchedProductCandidateFailsWithoutCreatingSku() {
		String email = runtimeQaEmail();
		String password = UUID.randomUUID().toString();
		Member member = member(email);
		Product mismatched = new Product(
				LocalQaBootstrapService.PRODUCT_NAME,
				"일치하지 않는 설명",
				LocalQaBootstrapService.PRODUCT_DESCRIPTION,
				LocalQaBootstrapService.PRODUCT_PET_TYPE,
				null,
				LocalQaBootstrapService.PRODUCT_DISPLAY_STATUS);
		when(memberRepository.findByEmailForUpdate(email)).thenReturn(Optional.of(member));
		when(passwordEncoder.matches(password, member.getPasswordHash())).thenReturn(true);
		when(productRepository.findAllByName(LocalQaBootstrapService.PRODUCT_NAME))
				.thenReturn(List.of(mismatched));

		assertThatThrownBy(() -> bootstrapService.bootstrap(email, password, false))
				.isInstanceOf(LocalQaBootstrapException.class);

		verifyNoInteractions(skuRepository, subscriptionRepository);
	}

	@Test
	void singleSkuWithMismatchedPriceFailsWithoutMutation() {
		Product product = matchingProduct();
		assertMismatchedSkuFails(product, new Sku(
				product,
				LocalQaBootstrapService.SKU_NAME,
				LocalQaBootstrapService.SKU_PRICE.add(BigDecimal.ONE),
				true,
				LocalQaBootstrapService.SKU_DISPLAY_ORDER));
	}

	@Test
	void singleSkuWithMismatchedSubscribableFlagFailsWithoutMutation() {
		Product product = matchingProduct();
		assertMismatchedSkuFails(product, new Sku(
				product,
				LocalQaBootstrapService.SKU_NAME,
				LocalQaBootstrapService.SKU_PRICE,
				false,
				LocalQaBootstrapService.SKU_DISPLAY_ORDER));
	}

	@Test
	void singleSkuWithMismatchedDisplayOrderFailsWithoutMutation() {
		Product product = matchingProduct();
		assertMismatchedSkuFails(product, new Sku(
				product,
				LocalQaBootstrapService.SKU_NAME,
				LocalQaBootstrapService.SKU_PRICE,
				true,
				LocalQaBootstrapService.SKU_DISPLAY_ORDER + 1));
	}

	@Test
	void resetDeletesOnlySubscriptionsOwnedByResolvedQaMember() {
		String email = runtimeQaEmail();
		String password = UUID.randomUUID().toString();
		Member member = member(email);
		ReflectionTestUtils.setField(member, "id", 42L);
		Product product = matchingProduct();
		Sku sku = matchingSku(product);
		when(memberRepository.findByEmailForUpdate(email)).thenReturn(Optional.of(member));
		when(passwordEncoder.matches(password, member.getPasswordHash())).thenReturn(true);
		when(productRepository.findAllByName(LocalQaBootstrapService.PRODUCT_NAME)).thenReturn(List.of(product));
		when(skuRepository.findAllByProductIdAndName(product.getId(), LocalQaBootstrapService.SKU_NAME))
				.thenReturn(List.of(sku));

		bootstrapService.bootstrap(email, password, true);

		verify(subscriptionRepository).deleteAllByMemberId(42L);
	}

	private String runtimeQaEmail() {
		return LocalQaBootstrapService.QA_EMAIL_LOCAL_PART + "@" + UUID.randomUUID() + ".example";
	}

	private Member member(String email) {
		return new Member(email, UUID.randomUUID().toString());
	}

	private Product matchingProduct() {
		Product product = new Product(
				LocalQaBootstrapService.PRODUCT_NAME,
				LocalQaBootstrapService.PRODUCT_SHORT_DESCRIPTION,
				LocalQaBootstrapService.PRODUCT_DESCRIPTION,
				LocalQaBootstrapService.PRODUCT_PET_TYPE,
				null,
				LocalQaBootstrapService.PRODUCT_DISPLAY_STATUS);
		ReflectionTestUtils.setField(product, "id", 100L);
		return product;
	}

	private Sku matchingSku(Product product) {
		return new Sku(
				product,
				LocalQaBootstrapService.SKU_NAME,
				LocalQaBootstrapService.SKU_PRICE,
				true,
				LocalQaBootstrapService.SKU_DISPLAY_ORDER);
	}

	private void assertMismatchedSkuFails(Product product, Sku sku) {
		String email = runtimeQaEmail();
		String password = UUID.randomUUID().toString();
		Member member = member(email);
		when(memberRepository.findByEmailForUpdate(email)).thenReturn(Optional.of(member));
		when(passwordEncoder.matches(password, member.getPasswordHash())).thenReturn(true);
		when(productRepository.findAllByName(LocalQaBootstrapService.PRODUCT_NAME)).thenReturn(List.of(product));
		when(skuRepository.findAllByProductIdAndName(product.getId(), LocalQaBootstrapService.SKU_NAME))
				.thenReturn(List.of(sku));

		assertThatThrownBy(() -> bootstrapService.bootstrap(email, password, false))
				.isInstanceOf(LocalQaBootstrapException.class);

		verify(skuRepository, never()).saveAndFlush(any());
		verifyNoInteractions(subscriptionRepository);
	}
}
