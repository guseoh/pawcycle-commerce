package com.pawcycle.backend.catalog.product.api;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.pawcycle.backend.catalog.product.application.ProductDetailUnavailableException;
import com.pawcycle.backend.catalog.product.application.ProductListUnavailableException;
import com.pawcycle.backend.catalog.product.application.ProductNotFoundException;
import com.pawcycle.backend.catalog.product.application.ProductQueryService;
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
		mockMvc = MockMvcBuilders
				.standaloneSetup(new ProductController(productQueryService))
				.setControllerAdvice(new ProductExceptionHandler())
				.build();
	}

	@Test
	void notFoundResponseMatchesApprovedContract() throws Exception {
		when(productQueryService.findProduct(99L)).thenThrow(new ProductNotFoundException());

		mockMvc.perform(get("/api/products/99"))
				.andExpect(status().isNotFound())
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
				.andExpect(jsonPath("$.code").value("PRODUCT_NOT_FOUND"))
				.andExpect(jsonPath("$.message").value("상품을 확인할 수 없습니다."))
				.andExpect(jsonPath("$.fieldErrors").isEmpty());
	}

	@Test
	void listFailureDoesNotExposeInternalDetails() throws Exception {
		when(productQueryService.findProducts())
				.thenThrow(new ProductListUnavailableException(new IllegalStateException("products table")));

		mockMvc.perform(get("/api/products"))
				.andExpect(status().isInternalServerError())
				.andExpect(jsonPath("$.code").value("PRODUCT_LIST_UNAVAILABLE"))
				.andExpect(jsonPath("$.message").value("상품 목록을 불러오지 못했습니다."))
				.andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.not(
						org.hamcrest.Matchers.containsString("products table"))))
				.andExpect(jsonPath("$.fieldErrors").isEmpty());
	}

	@Test
	void detailFailureDoesNotExposeInternalDetails() throws Exception {
		when(productQueryService.findProduct(1L))
				.thenThrow(new ProductDetailUnavailableException(new IllegalStateException("price column")));

		mockMvc.perform(get("/api/products/1"))
				.andExpect(status().isInternalServerError())
				.andExpect(jsonPath("$.code").value("PRODUCT_DETAIL_UNAVAILABLE"))
				.andExpect(jsonPath("$.message").value("상품 정보를 불러오지 못했습니다."))
				.andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.not(
						org.hamcrest.Matchers.containsString("price column"))))
				.andExpect(jsonPath("$.fieldErrors").isEmpty());
	}
}
