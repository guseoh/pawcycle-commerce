package com.pawcycle.backend.catalog.product.api;

import static org.springframework.restdocs.mockmvc.MockMvcRestDocumentation.document;
import static org.springframework.restdocs.mockmvc.MockMvcRestDocumentation.documentationConfiguration;
import static org.springframework.restdocs.operation.preprocess.Preprocessors.preprocessResponse;
import static org.springframework.restdocs.operation.preprocess.Preprocessors.prettyPrint;
import static org.springframework.restdocs.payload.PayloadDocumentation.fieldWithPath;
import static org.springframework.restdocs.payload.PayloadDocumentation.relaxedResponseFields;
import static org.springframework.restdocs.request.RequestDocumentation.parameterWithName;
import static org.springframework.restdocs.request.RequestDocumentation.pathParameters;
import static org.springframework.restdocs.request.RequestDocumentation.queryParameters;
import static org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.get;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.pawcycle.backend.catalog.maintenance.persistence.DemoCatalogImportPersistence;
import com.pawcycle.backend.foundation.bootstrap.LocalCommerceDemoFixtureService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.restdocs.RestDocumentationContextProvider;
import org.springframework.restdocs.RestDocumentationExtension;
import org.springframework.restdocs.payload.JsonFieldType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

/**
 * Spring REST Docs for the public product API. A documented field that disappears from the real
 * response fails this test, so the generated snippets in build/generated-snippets stay verified.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
@ExtendWith(RestDocumentationExtension.class)
class ProductApiDocumentationTests {
  private final WebApplicationContext applicationContext;
  private final LocalCommerceDemoFixtureService fixtureService;
  private MockMvc mockMvc;

  @Autowired
  ProductApiDocumentationTests(
      WebApplicationContext applicationContext, DemoCatalogImportPersistence importService) {
    this.applicationContext = applicationContext;
    this.fixtureService = new LocalCommerceDemoFixtureService(importService);
  }

  @BeforeEach
  void setUp(RestDocumentationContextProvider restDocumentation) {
    mockMvc =
        MockMvcBuilders.webAppContextSetup(applicationContext)
            .apply(springSecurity())
            .apply(documentationConfiguration(restDocumentation))
            .build();
    fixtureService.bootstrap();
  }

  @Test
  void documentsProductList() throws Exception {
    mockMvc
        .perform(
            get("/api/products")
                .param("q", "사료")
                .param("petType", "DOG")
                .param("category", "food")
                .param("page", "0")
                .param("size", "2")
                .param("sort", "PRICE_ASC"))
        .andExpect(status().isOk())
        .andDo(
            document(
                "products-list",
                preprocessResponse(prettyPrint()),
                queryParameters(
                    parameterWithName("q").description("상품명·한 줄 설명·설명의 부분 일치 검색어. `%`, `_`도 글자로 처리한다").optional(),
                    parameterWithName("petType").description("반려동물 종류 (`DOG`, `CAT`)").optional(),
                    parameterWithName("category").description("카테고리 slug. 상위 카테고리 slug면 하위 카테고리 상품도 포함한다").optional(),
                    parameterWithName("page").description("0부터 시작하는 페이지 번호 (기본 0)").optional(),
                    parameterWithName("size").description("페이지 크기 (기본 20)").optional(),
                    parameterWithName("sort").description("`RECOMMENDED`, `NEWEST`(기본), `PRICE_ASC`, `PRICE_DESC`, `RATING`, `REVIEW_COUNT`").optional()),
                relaxedResponseFields(
                    fieldWithPath("items[]").type(JsonFieldType.ARRAY).description("상품 요약 목록"),
                    fieldWithPath("items[].productId").type(JsonFieldType.NUMBER).description("상품 ID"),
                    fieldWithPath("items[].name").type(JsonFieldType.STRING).description("상품명"),
                    fieldWithPath("items[].petType").type(JsonFieldType.STRING).description("반려동물 종류"),
                    fieldWithPath("items[].category.slug").type(JsonFieldType.STRING).description("카테고리 slug"),
                    fieldWithPath("items[].representativePrice").type(JsonFieldType.NUMBER).description("대표 판매가").optional(),
                    fieldWithPath("items[].purchasable").type(JsonFieldType.BOOLEAN).description("지금 구매할 수 있는지"),
                    fieldWithPath("items[].hasSubscribableSku").type(JsonFieldType.BOOLEAN).description("정기배송 가능한 SKU가 있는지"),
                    fieldWithPath("items[].reviewCount").type(JsonFieldType.NUMBER).description("리뷰 수"),
                    fieldWithPath("page").type(JsonFieldType.NUMBER).description("현재 페이지"),
                    fieldWithPath("size").type(JsonFieldType.NUMBER).description("페이지 크기"),
                    fieldWithPath("totalElements").type(JsonFieldType.NUMBER).description("전체 상품 수"),
                    fieldWithPath("totalPages").type(JsonFieldType.NUMBER).description("전체 페이지 수"))));
  }

  @Test
  void documentsProductDetail() throws Exception {
    String list =
        mockMvc
            .perform(get("/api/products").param("petType", "DOG").param("category", "food"))
            .andReturn()
            .getResponse()
            .getContentAsString();
    Number productId = JsonPath.read(list, "$.items[0].productId");

    mockMvc
        .perform(get("/api/products/{productId}", productId))
        .andExpect(status().isOk())
        .andDo(
            document(
                "products-detail",
                preprocessResponse(prettyPrint()),
                pathParameters(parameterWithName("productId").description("상품 ID. 숫자가 아니거나 공개되지 않은 상품이면 404")),
                relaxedResponseFields(
                    fieldWithPath("productId").type(JsonFieldType.NUMBER).description("상품 ID"),
                    fieldWithPath("name").type(JsonFieldType.STRING).description("상품명"),
                    fieldWithPath("petType").type(JsonFieldType.STRING).description("반려동물 종류"),
                    fieldWithPath("category.slug").type(JsonFieldType.STRING).description("카테고리 slug"),
                    fieldWithPath("skus[]").type(JsonFieldType.ARRAY).description("판매 단위(SKU) 목록"),
                    fieldWithPath("purchasable").type(JsonFieldType.BOOLEAN).description("구매 가능한 SKU가 하나라도 있는지"),
                    fieldWithPath("detailSections").type(JsonFieldType.ARRAY).description("상세 설명 섹션"),
                    fieldWithPath("trust.reviewCount").type(JsonFieldType.NUMBER).description("리뷰 수"),
                    fieldWithPath("trust.questionCount").type(JsonFieldType.NUMBER).description("상품 문의 수"))));
  }
}
