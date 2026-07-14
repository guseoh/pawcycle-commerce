package com.pawcycle.backend.foundation.bootstrap;

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
import java.util.Objects;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class LocalQaBootstrapService {

	static final String QA_EMAIL_LOCAL_PART = "qa-foundation-004";
	static final String PRODUCT_NAME = "[QA FOUNDATION-004] 정기배송 사료";
	static final String PRODUCT_SHORT_DESCRIPTION = "로컬 브라우저 통합 검증 전용 상품";
	static final String PRODUCT_DESCRIPTION = "FOUNDATION-004 local-only QA fixture";
	static final String PRODUCT_PET_TYPE = "DOG";
	static final String PRODUCT_DISPLAY_STATUS = "PUBLIC";
	static final String SKU_NAME = "[QA FOUNDATION-004] 2kg";
	static final BigDecimal SKU_PRICE = new BigDecimal("19900.00");
	static final int SKU_DISPLAY_ORDER = 1;

	private final EmailNormalizer emailNormalizer;
	private final PasswordEncoder passwordEncoder;
	private final MemberRepository memberRepository;
	private final ProductRepository productRepository;
	private final SkuRepository skuRepository;
	private final SubscriptionRepository subscriptionRepository;

	public LocalQaBootstrapService(
			EmailNormalizer emailNormalizer,
			PasswordEncoder passwordEncoder,
			MemberRepository memberRepository,
			ProductRepository productRepository,
			SkuRepository skuRepository,
			SubscriptionRepository subscriptionRepository) {
		this.emailNormalizer = emailNormalizer;
		this.passwordEncoder = passwordEncoder;
		this.memberRepository = memberRepository;
		this.productRepository = productRepository;
		this.skuRepository = skuRepository;
		this.subscriptionRepository = subscriptionRepository;
	}

	@Transactional
	public void bootstrap(String email, String password, boolean resetSubscriptions) {
		String normalizedEmail = validateCredentials(email, password);
		Member member = loadOrCreateMember(normalizedEmail, password);
		Product product = loadOrCreateProduct();
		loadOrCreateSku(product);
		if (resetSubscriptions) {
			subscriptionRepository.deleteAllByMemberId(member.getId());
		}
	}

	private String validateCredentials(String email, String password) {
		if (password == null || password.isEmpty()) {
			throw invalidCredentialConfiguration();
		}
		String normalizedEmail;
		try {
			normalizedEmail = emailNormalizer.normalizeEmail(email);
		} catch (AuthValidationException exception) {
			throw invalidCredentialConfiguration();
		}
		int separator = normalizedEmail.indexOf('@');
		if (!normalizedEmail.substring(0, separator).equals(QA_EMAIL_LOCAL_PART)) {
			throw invalidCredentialConfiguration();
		}
		return normalizedEmail;
	}

	private Member loadOrCreateMember(String email, String password) {
		return memberRepository.findByEmailForUpdate(email)
				.map(member -> validateExistingMember(member, password))
				.orElseGet(() -> memberRepository.saveAndFlush(
						new Member(email, passwordEncoder.encode(password))));
	}

	private Member validateExistingMember(Member member, String password) {
		if (!passwordEncoder.matches(password, member.getPasswordHash())) {
			throw new LocalQaBootstrapException("로컬 QA bootstrap 회원이 기존 데이터와 충돌합니다.");
		}
		return member;
	}

	private Product loadOrCreateProduct() {
		List<Product> candidates = productRepository.findAllByName(PRODUCT_NAME);
		if (candidates.isEmpty()) {
			return productRepository.saveAndFlush(new Product(
					PRODUCT_NAME,
					PRODUCT_SHORT_DESCRIPTION,
					PRODUCT_DESCRIPTION,
					PRODUCT_PET_TYPE,
					null,
					PRODUCT_DISPLAY_STATUS));
		}
		if (candidates.size() != 1 || !matchesProductFixture(candidates.get(0))) {
			throw new LocalQaBootstrapException("로컬 QA bootstrap 상품 fixture가 기존 데이터와 충돌합니다.");
		}
		return candidates.get(0);
	}

	private boolean matchesProductFixture(Product product) {
		return PRODUCT_NAME.equals(product.getName())
				&& PRODUCT_SHORT_DESCRIPTION.equals(product.getShortDescription())
				&& PRODUCT_DESCRIPTION.equals(product.getDescription())
				&& PRODUCT_PET_TYPE.equals(product.getPetType())
				&& product.getThumbnailUrl() == null
				&& PRODUCT_DISPLAY_STATUS.equals(product.getDisplayStatus());
	}

	private Sku loadOrCreateSku(Product product) {
		List<Sku> candidates = skuRepository.findAllByProductIdAndName(product.getId(), SKU_NAME);
		if (candidates.isEmpty()) {
			return skuRepository.saveAndFlush(new Sku(
					product,
					SKU_NAME,
					SKU_PRICE,
					true,
					SKU_DISPLAY_ORDER));
		}
		if (candidates.size() != 1 || !matchesSkuFixture(candidates.get(0), product)) {
			throw new LocalQaBootstrapException("로컬 QA bootstrap SKU fixture가 기존 데이터와 충돌합니다.");
		}
		return candidates.get(0);
	}

	private boolean matchesSkuFixture(Sku sku, Product product) {
		return Objects.equals(product.getId(), sku.getProduct().getId())
				&& SKU_NAME.equals(sku.getName())
				&& SKU_PRICE.compareTo(sku.getPrice()) == 0
				&& sku.isSubscribable()
				&& sku.getDisplayOrder() == SKU_DISPLAY_ORDER;
	}

	private LocalQaBootstrapException invalidCredentialConfiguration() {
		return new LocalQaBootstrapException("로컬 QA bootstrap credential 설정이 없거나 유효하지 않습니다.");
	}
}
