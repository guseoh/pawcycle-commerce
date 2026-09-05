package com.pawcycle.backend.catalog.product.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.pawcycle.backend.catalog.category.domain.Category;
import com.pawcycle.backend.catalog.product.domain.Product;
import com.pawcycle.backend.catalog.product.persistence.ProductRepository;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.annotation.Transactional;

@ExtendWith(MockitoExtension.class)
class ProductQueryServiceTests {
  @Mock private ProductRepository productRepository;
  @Mock private ProductDiscoveryReader productDiscoveryReader;
  @Mock private ProductDetailContentReader productDetailContentReader;
  private ProductQueryService productQueryService;

  @BeforeEach
  void setUp() {
    productQueryService =
        new ProductQueryService(
            productRepository, productDiscoveryReader, productDetailContentReader);
  }

  @Test
  void listAlwaysUsesAuthoritativeDiscoveryReader() {
    ProductListView expected = new ProductListView(List.of(), 0, 20, 0);
    doReturn(expected)
        .when(productDiscoveryReader)
        .read(
            isNull(),
            isNull(),
            isNull(),
            isNull(),
            isNull(),
            anyList(),
            isNull(),
            isNull(),
            isNull(),
            isNull(),
            eq(0),
            eq(20),
            eq(ProductSort.NEWEST));

    assertThat(productQueryService.findProducts()).isSameAs(expected);
    verify(productDiscoveryReader)
        .read(
            null,
            null,
            null,
            null,
            null,
            List.of(),
            null,
            null,
            null,
            null,
            0,
            20,
            ProductSort.NEWEST);
    verifyNoInteractions(productRepository, productDetailContentReader);
  }

  @Test
  void malformedFacetIsRejectedBeforeDiscoveryFailureWrapping() {
    assertThatThrownBy(
            () ->
                productQueryService.findProducts(
                    null,
                    null,
                    null,
                    null,
                    null,
                    List.of("broken-facet"),
                    null,
                    null,
                    null,
                    null,
                    0,
                    20,
                    ProductSort.NEWEST))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("facet은 key:value 형식이어야 합니다.");

    verifyNoInteractions(productDiscoveryReader);
  }

  @Test
  void detailUsesDiscoveryAndContentReadersWithoutFallback() {
    Product product = product(1L, "상품", "DOG", "짧은 설명");
    when(productRepository.findPublicById(1L)).thenReturn(Optional.of(product));
    when(productDiscoveryReader.readDetailSkus(1L))
        .thenReturn(
            List.of(
                new ProductDetailSkuRow(
                    10L,
                    "2kg",
                    new BigDecimal("19900"),
                    new BigDecimal("22000"),
                    true,
                    3,
                    List.of())));
    when(productDiscoveryReader.readDetailSupplement(1L))
        .thenReturn(
            new ProductDetailSupplement(
                new BrandSummary(2L, "브랜드", "brand", null), List.of(), List.of()));
    when(productDetailContentReader.visibleSections(1L)).thenReturn(List.of());
    when(productDetailContentReader.trust(1L))
        .thenReturn(new ProductTrustProjection(null, 0, 0));

    ProductDetailView response = productQueryService.findProduct(1L);

    assertThat(response.skus()).hasSize(1);
    assertThat(response.skus().getFirst().availableDeliveryCycles()).containsExactly(2, 4, 8);
    assertThat(response.brand().slug()).isEqualTo("brand");
    verify(productDiscoveryReader).readDetailSkus(1L);
    verify(productDiscoveryReader).readDetailSupplement(1L);
    verify(productDetailContentReader).visibleSections(1L);
    verify(productDetailContentReader).trust(1L);
  }

  @Test
  void missingProductUsesNotFoundException() {
    when(productRepository.findPublicById(99L)).thenReturn(Optional.empty());
    assertThatThrownBy(() -> productQueryService.findProduct(99L))
        .isInstanceOf(ProductNotFoundException.class)
        .hasMessage("상품을 확인할 수 없습니다.");
    verifyNoInteractions(productDiscoveryReader, productDetailContentReader);
  }

  @Test
  void unexpectedRepositoryFailuresUseEndpointSpecificExceptions() {
    when(productDiscoveryReader.read(
            any(),
            any(),
            any(),
            any(),
            any(),
            anyList(),
            any(),
            any(),
            any(),
            any(),
            eq(0),
            eq(20),
            eq(ProductSort.NEWEST)))
        .thenThrow(new IllegalStateException("query details"));
    when(productRepository.findPublicById(1L))
        .thenThrow(new IllegalStateException("column details"));

    assertThatThrownBy(productQueryService::findProducts)
        .isInstanceOf(ProductListUnavailableException.class);
    assertThatThrownBy(() -> productQueryService.findProduct(1L))
        .isInstanceOf(ProductDetailUnavailableException.class);
  }

  @Test
  void hasOneConstructorAndReadOnlyDetailTransaction() throws NoSuchMethodException {
    assertThat(ProductQueryService.class.getDeclaredConstructors()).hasSize(1);
    Transactional detailTransaction =
        ProductQueryService.class
            .getMethod("findProduct", Long.class)
            .getAnnotation(Transactional.class);
    assertThat(detailTransaction).isNotNull();
    assertThat(detailTransaction.readOnly()).isTrue();
  }

  private Product product(Long id, String name, String petType, String shortDescription) {
    Product product = mock(Product.class);
    when(product.getId()).thenReturn(id);
    when(product.getName()).thenReturn(name);
    when(product.getPetType()).thenReturn(petType);
    when(product.getShortDescription()).thenReturn(shortDescription);
    when(product.getDescription()).thenReturn(null);
    when(product.getThumbnailUrl()).thenReturn(null);
    Category category = mock(Category.class);
    when(category.getId()).thenReturn(1L);
    when(category.getName()).thenReturn("사료");
    when(category.getSlug()).thenReturn("food");
    when(product.getCategory()).thenReturn(category);
    return product;
  }
}
