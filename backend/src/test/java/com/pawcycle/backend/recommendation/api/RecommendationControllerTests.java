package com.pawcycle.backend.recommendation.api;

import com.pawcycle.backend.recommendation.application.RecommendationService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pawcycle.backend.member.application.AuthenticatedMemberPrincipal;
import java.util.List;
import com.pawcycle.backend.recommendation.domain.RecommendationCategory;
import com.pawcycle.backend.recommendation.model.RecommendationView;
import com.pawcycle.backend.recommendation.model.RecommendationView.ItemView;
import org.junit.jupiter.api.Test;

class RecommendationControllerTests {
  @Test
  void principalAndPetIdAreBoundToServiceAndResponseSerializesAsContracted() throws Exception {
    RecommendationService service = mock(RecommendationService.class);
    RecommendationResponse response =
        new RecommendationResponse(
            List.of(
                new RecommendationItem(
                    301L,
                    "성견 사료",
                    "매일 먹는 기본 사료",
                    null,
                    new RecommendationItemCategory(10L, "사료", "food"),
                    "반려동물 유형에 잘 맞는 상품입니다.")));
    when(service.recommend(7L, 42L)).thenReturn(new RecommendationView(response.requestId(), List.of(new ItemView(301L, "성견 사료",
        "매일 먹는 기본 사료", null, new RecommendationCategory(10L, "사료", "food"),
        "반려동물 유형에 잘 맞는 상품입니다.", "PERSONALIZED"))));
    RecommendationController controller = new RecommendationController(service);

    RecommendationResponse actual =
        controller.products(new AuthenticatedMemberPrincipal(7L), 42L);

    verify(service).recommend(7L, 42L);
    String json = new ObjectMapper().writeValueAsString(actual);
    assertThat(json).contains("\"productId\":301");
    assertThat(json).contains("\"categoryId\":10");
    assertThat(json).contains("\"name\":\"사료\"");
    assertThat(json).contains("\"slug\":\"food\"");
    assertThat(json).contains("\"reason\":\"반려동물 유형에 잘 맞는 상품입니다.\"");
  }
}
