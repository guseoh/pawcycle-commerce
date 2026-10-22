package com.pawcycle.backend.catalog.product.api;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.pawcycle.backend.catalog.product.application.ProductDetailUnavailableException;
import com.pawcycle.backend.catalog.product.application.ProductListUnavailableException;
import com.pawcycle.backend.catalog.product.application.ProductListView;
import com.pawcycle.backend.catalog.product.application.ProductNotFoundException;
import com.pawcycle.backend.catalog.product.application.ProductQueryService;
import com.pawcycle.backend.catalog.product.application.ProductSort;
import com.pawcycle.backend.common.api.CommonValidationExceptionHandler;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class ProductControllerTests {

  private ProductQueryService productQueryService;
  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    productQueryService = mock(ProductQueryService.class);
    mockMvc =
        MockMvcBuilders.standaloneSetup(new ProductController(productQueryService))
            .setControllerAdvice(new CommonValidationExceptionHandler(), new ProductExceptionHandler())
            .build();
  }

  @Test
  void notFoundResponseMatchesApprovedContract() throws Exception {
    when(productQueryService.findProduct(99L)).thenThrow(new ProductNotFoundException());

    mockMvc
        .perform(get("/api/products/99"))
        .andExpect(status().isNotFound())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.code").value("PRODUCT_NOT_FOUND"))
        .andExpect(jsonPath("$.message").value("상품을 확인할 수 없습니다."))
        .andExpect(jsonPath("$.fieldErrors").isEmpty());
  }

  @Test
  void pageableListUsesItemsResponseContract() throws Exception {
    when(productQueryService.findProducts(null, null, null, 0, 20, ProductSort.NEWEST))
        .thenReturn(new ProductListView(List.of(), 0, 20, 0));

    mockMvc
        .perform(get("/api/products"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items").isArray())
        .andExpect(jsonPath("$.page").value(0))
        .andExpect(jsonPath("$.size").value(20))
        .andExpect(jsonPath("$.totalElements").value(0))
        .andExpect(jsonPath("$.totalPages").value(0));

    verify(productQueryService).findProducts(null, null, null, 0, 20, ProductSort.NEWEST);
    verifyNoMoreInteractions(productQueryService);
  }

  @Test
  void extendedFiltersUseOneCurrentServiceCall() throws Exception {
    BigDecimal minPrice = new BigDecimal("1000");
    BigDecimal maxPrice = new BigDecimal("2000");
    when(productQueryService.findProducts(
            "food", "DOG", "food", "dry", "brand", List.of("size:small"),
            minPrice, maxPrice, true, false, 1, 5, ProductSort.PRICE_ASC))
        .thenReturn(new ProductListView(List.of(), 1, 5, 0));

    mockMvc
        .perform(get("/api/products")
            .param("q", "food").param("petType", "DOG").param("category", "food")
            .param("subcategory", "dry").param("brand", "brand").param("facet", "size:small")
            .param("minPrice", "1000").param("maxPrice", "2000")
            .param("subscribable", "true").param("purchasable", "false")
            .param("page", "1").param("size", "5").param("sort", "PRICE_ASC"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items").isEmpty())
        .andExpect(jsonPath("$.page").value(1))
        .andExpect(jsonPath("$.size").value(5));

    verify(productQueryService).findProducts(
        "food", "DOG", "food", "dry", "brand", List.of("size:small"),
        minPrice, maxPrice, true, false, 1, 5, ProductSort.PRICE_ASC);
    verifyNoMoreInteractions(productQueryService);
  }

  @Test
  void listFailureDoesNotExposeInternalDetails() throws Exception {
    when(productQueryService.findProducts(null, null, null, 0, 20, ProductSort.NEWEST))
        .thenThrow(
            new ProductListUnavailableException(new IllegalStateException("products table")));

    mockMvc
        .perform(get("/api/products"))
        .andExpect(status().isInternalServerError())
        .andExpect(jsonPath("$.code").value("PRODUCT_LIST_UNAVAILABLE"))
        .andExpect(jsonPath("$.message").value("상품 목록을 불러오지 못했습니다."))
        .andExpect(
            jsonPath("$.message")
                .value(
                    org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("products table"))))
        .andExpect(jsonPath("$.fieldErrors").isEmpty());
  }

  @Test
  void detailFailureDoesNotExposeInternalDetails() throws Exception {
    when(productQueryService.findProduct(1L))
        .thenThrow(
            new ProductDetailUnavailableException(new IllegalStateException("price column")));

    mockMvc
        .perform(get("/api/products/1"))
        .andExpect(status().isInternalServerError())
        .andExpect(jsonPath("$.code").value("PRODUCT_DETAIL_UNAVAILABLE"))
        .andExpect(jsonPath("$.message").value("상품 정보를 불러오지 못했습니다."))
        .andExpect(
            jsonPath("$.message")
                .value(
                    org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("price column"))))
        .andExpect(jsonPath("$.fieldErrors").isEmpty());
  }
}
