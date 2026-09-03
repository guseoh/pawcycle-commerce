package com.pawcycle.backend.foundation.bootstrap;

import com.pawcycle.backend.catalog.category.domain.Category;
import com.pawcycle.backend.catalog.category.persistence.CategoryRepository;
import com.pawcycle.backend.catalog.product.domain.Product;
import com.pawcycle.backend.catalog.product.persistence.ProductRepository;
import com.pawcycle.backend.catalog.sku.domain.Sku;
import com.pawcycle.backend.catalog.sku.domain.SkuStatus;
import com.pawcycle.backend.catalog.sku.persistence.SkuRepository;
import com.pawcycle.backend.member.application.AuthValidationException;
import com.pawcycle.backend.member.application.EmailNormalizer;
import com.pawcycle.backend.member.domain.Member;
import com.pawcycle.backend.member.persistence.MemberRepository;
import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;
import org.springframework.beans.factory.annotation.Autowired;
import com.pawcycle.backend.foundation.persistence.NativeQueryExecutor;
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
  static final String QA_CATEGORY_SLUG = "qa-foundation-004";
  static final String SKU_NAME = "[QA FOUNDATION-004] 2kg";
  static final String SKU_CODE = "QA-FOUNDATION-004-SKU";
  static final BigDecimal SKU_PRICE = new BigDecimal("19900.00");
  static final int SKU_DISPLAY_ORDER = 1;

  private final EmailNormalizer emailNormalizer;
  private final PasswordEncoder passwordEncoder;
  private final MemberRepository memberRepository;
  private final ProductRepository productRepository;
  private final CategoryRepository categoryRepository;
  private final SkuRepository skuRepository;
  private final NativeQueryExecutor jdbcTemplate;

  LocalQaBootstrapService(
      EmailNormalizer emailNormalizer,
      PasswordEncoder passwordEncoder,
      MemberRepository memberRepository,
      ProductRepository productRepository,
      SkuRepository skuRepository,
      NativeQueryExecutor jdbcTemplate) {
    this(
        emailNormalizer,
        passwordEncoder,
        memberRepository,
        productRepository,
        null,
        skuRepository,
        jdbcTemplate);
  }

  @Autowired
  public LocalQaBootstrapService(
      EmailNormalizer emailNormalizer,
      PasswordEncoder passwordEncoder,
      MemberRepository memberRepository,
      ProductRepository productRepository,
      CategoryRepository categoryRepository,
      SkuRepository skuRepository,
      NativeQueryExecutor jdbcTemplate) {
    this.emailNormalizer = emailNormalizer;
    this.passwordEncoder = passwordEncoder;
    this.memberRepository = memberRepository;
    this.productRepository = productRepository;
    this.categoryRepository = categoryRepository;
    this.skuRepository = skuRepository;
    this.jdbcTemplate = jdbcTemplate;
  }

  @Transactional
  public void bootstrap(String email, String password, boolean resetSubscriptions) {
    bootstrap(email, password, resetSubscriptions, false);
  }

  @Transactional
  public void bootstrap(
      String email, String password, boolean resetSubscriptions, boolean exposeProductFixture) {
    String normalizedEmail = validateCredentials(email, password);
    Member member = loadOrCreateMember(normalizedEmail, password);
    Product product = loadOrCreateProduct(exposeProductFixture);
    Sku sku = loadOrCreateSku(product);
    ensureInventory(sku);
    if (resetSubscriptions) {
      deleteSubscriptionChildren(member.getId());
      if (jdbcTemplate != null) {
        jdbcTemplate.update("DELETE FROM subscriptions WHERE member_id=?", member.getId());
      }
    }
  }

  private void ensureInventory(Sku sku) {
    if (jdbcTemplate == null) return;
    jdbcTemplate.update(
        "INSERT IGNORE INTO inventories(sku_id,available_quantity,reserved_quantity,version) VALUES"
            + " (?,0,0,0)",
        sku.getId());
  }

  private void deleteSubscriptionChildren(Long memberId) {
    if (jdbcTemplate == null) return;
    jdbcTemplate.update(
        "DELETE p FROM pending_plan_changes p JOIN subscriptions s ON s.id=p.subscription_id WHERE"
            + " s.member_id=?",
        memberId);
    jdbcTemplate.update(
        "DELETE r FROM subscription_command_idempotency_results r JOIN subscriptions s ON"
            + " s.id=r.subscription_id WHERE s.member_id=?",
        memberId);
    jdbcTemplate.update(
        "DELETE FROM subscription_creation_idempotency_results WHERE member_id=?", memberId);
    jdbcTemplate.update(
        "DELETE h FROM subscription_command_history h JOIN subscriptions s ON"
            + " s.id=h.subscription_id WHERE s.member_id=?",
        memberId);
    jdbcTemplate.update(
        "DELETE item FROM subscription_order_items item JOIN subscription_orders orders ON"
            + " orders.id=item.order_id JOIN subscriptions s ON s.id=orders.subscription_id WHERE"
            + " s.member_id=?",
        memberId);
    jdbcTemplate.update(
        "DELETE orders FROM subscription_orders orders JOIN subscriptions s ON"
            + " s.id=orders.subscription_id WHERE s.member_id=?",
        memberId);
    jdbcTemplate.update(
        "DELETE sc FROM subscription_schedules sc JOIN subscriptions s ON s.id=sc.subscription_id"
            + " WHERE s.member_id=?",
        memberId);
    jdbcTemplate.update(
        "DELETE si FROM subscription_snapshot_items si JOIN subscription_snapshots ss ON"
            + " ss.id=si.snapshot_id JOIN subscriptions s ON s.id=ss.subscription_id WHERE"
            + " s.member_id=?",
        memberId);
    jdbcTemplate.update(
        "UPDATE subscriptions SET current_snapshot_id=NULL WHERE member_id=?", memberId);
    jdbcTemplate.update(
        "DELETE ss FROM subscription_snapshots ss JOIN subscriptions s ON s.id=ss.subscription_id"
            + " WHERE s.member_id=?",
        memberId);
  }

  private String validateCredentials(String email, String password) {
    if (password == null || password.isEmpty()) {
      throw invalidCredentialConfiguration();
    }
    String normalizedEmail;
    try {
      normalizedEmail = emailNormalizer.normalizeEmail(email);
    } catch (AuthValidationException exception) {
      throw invalidCredentialConfiguration(exception);
    }
    int separator = normalizedEmail.indexOf('@');
    if (!normalizedEmail.substring(0, separator).equals(QA_EMAIL_LOCAL_PART)) {
      throw invalidCredentialConfiguration();
    }
    return normalizedEmail;
  }

  private Member loadOrCreateMember(String email, String password) {
    return memberRepository
        .findByEmailForUpdate(email)
        .map(member -> validateExistingMember(member, password))
        .orElseGet(
            () ->
                memberRepository.saveAndFlush(new Member(email, passwordEncoder.encode(password))));
  }

  private Member validateExistingMember(Member member, String password) {
    if (!passwordEncoder.matches(password, member.getPasswordHash())) {
      throw new LocalQaBootstrapException("로컬 QA bootstrap 회원이 기존 데이터와 충돌합니다.");
    }
    return member;
  }

  private Product loadOrCreateProduct(boolean exposeProductFixture) {
    List<Product> candidates = productRepository.findAllByName(PRODUCT_NAME);
    if (candidates.isEmpty()) {
      Category category =
          categoryRepository == null
              ? null
              : categoryRepository
                  .findBySlug(QA_CATEGORY_SLUG)
                  .map(
                      existingCategory ->
                          alignFixtureCategoryVisibility(existingCategory, exposeProductFixture))
                  .orElseGet(
                      () ->
                          categoryRepository.saveAndFlush(
                              new Category(
                                  "QA Foundation", QA_CATEGORY_SLUG, 0, exposeProductFixture)));
      return productRepository.saveAndFlush(
          new Product(
              category,
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
    Product product = candidates.get(0);
    if (product.getCategory() != null && QA_CATEGORY_SLUG.equals(product.getCategory().getSlug())) {
      alignFixtureCategoryVisibility(product.getCategory(), exposeProductFixture);
    }
    return product;
  }

  private Category alignFixtureCategoryVisibility(Category category, boolean exposeProductFixture) {
    category.update(null, null, null, exposeProductFixture);
    return category;
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
      return skuRepository.saveAndFlush(
          new Sku(
              product, SKU_CODE, SKU_NAME, SKU_PRICE, true, SKU_DISPLAY_ORDER, SkuStatus.ACTIVE));
    }
    if (candidates.size() != 1 || !matchesSkuFixture(candidates.get(0), product)) {
      throw new LocalQaBootstrapException("로컬 QA bootstrap SKU fixture가 기존 데이터와 충돌합니다.");
    }
    return candidates.get(0);
  }

  private boolean matchesSkuFixture(Sku sku, Product product) {
    return Objects.equals(product.getId(), sku.getProduct().getId())
        && SKU_CODE.equals(sku.getSkuCode())
        && SKU_NAME.equals(sku.getName())
        && SKU_PRICE.compareTo(sku.getPrice()) == 0
        && sku.isSubscribable()
        && sku.getDisplayOrder() == SKU_DISPLAY_ORDER
        && sku.getStatus() == SkuStatus.ACTIVE;
  }

  private LocalQaBootstrapException invalidCredentialConfiguration() {
    return new LocalQaBootstrapException("로컬 QA bootstrap credential 설정이 없거나 유효하지 않습니다.");
  }

  private LocalQaBootstrapException invalidCredentialConfiguration(Throwable cause) {
    return new LocalQaBootstrapException("로컬 QA bootstrap credential 설정이 없거나 유효하지 않습니다.", cause);
  }
}
