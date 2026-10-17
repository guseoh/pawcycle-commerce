package com.pawcycle.backend.recommendation.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.pawcycle.backend.recommendation.application.ProductRecommendationService;
import com.pawcycle.backend.recommendation.domain.RecommendationCategory;
import com.pawcycle.backend.recommendation.model.RecommendationView;
import com.pawcycle.backend.recommendation.model.RecommendationView.ItemView;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import tools.jackson.databind.ObjectMapper;

class RecommendationResponseBoundaryTests {
  private static final ObjectMapper JSON = new ObjectMapper();

  static Stream<Arguments> values() {
    return IntStream.range(0, 256).mapToObj(pattern -> Arguments.of(pattern));
  }

  @ParameterizedTest
  @MethodSource("values")
  void itemCategoryAndResponseKeepFrozenArgumentsNullsOrderAndJson(int pattern) throws Exception {
    RecommendationCategory category = (pattern & 1) != 0 ? null : new RecommendationCategory(19L,
        (pattern & 2) != 0 ? null : "사료", (pattern & 4) != 0 ? null : "food");
    var first = new ItemView(71L, nullable(pattern, 8, "상품"), nullable(pattern, 16, "설명"),
        nullable(pattern, 32, "https://example.test/image"), category, nullable(pattern, 64, "추천 이유"),
        nullable(pattern, 128, "PERSONALIZED"));
    var second = new ItemView(13L, "다른 상품", "", null, null, "", "EXPLORATION");
    var view = new RecommendationView("request-" + pattern, List.of(first, second, first));
    var expected = frozen(view);
    var actual = RecommendationResponse.from(view);
    assertThat(actual).isEqualTo(expected);
    assertThat(JSON.writeValueAsString(actual)).isEqualTo(JSON.writeValueAsString(expected));
    assertThat(RecommendationItem.from(first)).isEqualTo(expected.products().getFirst());
    assertThat(RecommendationItemCategory.from(category)).isEqualTo(expected.products().getFirst().category());
  }

  @ParameterizedTest
  @ValueSource(strings = {"POPULAR", "TRENDING", "RELATED", "COMPLEMENTARY", "PERSONALIZED", "EXPLORATION"})
  void strategyIsPassedThroughWithoutNormalization(String strategy) {
    var view = new RecommendationView(null, List.of(new ItemView(1L, null, null, null, null, null, strategy)));
    assertThat(RecommendationResponse.from(view)).isEqualTo(frozen(view));
    assertThat(RecommendationResponse.from(view).requestId()).isNull();
  }

  @Test
  void mappingNeverRegeneratesRequestIdAndKeepsEmptyLists() {
    var view = new RecommendationView("request-created-in-use-case", List.of());
    try (var uuid = mockStatic(UUID.class)) {
      assertThat(RecommendationResponse.from(view)).isEqualTo(new RecommendationResponse(view.requestId(), List.of()));
      assertThat(RecommendationResponse.from(view).requestId()).isEqualTo(view.requestId());
      uuid.verifyNoInteractions();
    }
  }

  @Test
  void originalConstructorsAndUseCaseSnapshotKeepCopyAndNullFailureContracts() {
    var category = new RecommendationItemCategory(1L, null, null);
    var legacyItem = new RecommendationItem(7L, "상품", null, null, category, null);
    assertThat(legacyItem.strategy()).isEqualTo("PERSONALIZED");
    List<RecommendationItem> input = new ArrayList<>(List.of(legacyItem));
    var first = new RecommendationResponse(input);
    var second = new RecommendationResponse(input);
    assertThat(UUID.fromString(first.requestId())).isNotEqualTo(UUID.fromString(second.requestId()));
    input.clear();
    assertThat(first.products()).containsExactly(legacyItem);
    assertThatThrownBy(() -> first.products().clear()).isInstanceOf(UnsupportedOperationException.class);
    assertThatThrownBy(() -> new RecommendationResponse("id", null)).isInstanceOf(NullPointerException.class);
    assertThatThrownBy(() -> new RecommendationResponse("id", Arrays.asList((RecommendationItem) null))).isInstanceOf(NullPointerException.class);
    assertThatThrownBy(() -> new RecommendationView("id", null)).isInstanceOf(NullPointerException.class);
    assertThatThrownBy(() -> new RecommendationView("id", Arrays.asList((ItemView) null))).isInstanceOf(NullPointerException.class);
    var item = new ItemView(7L, null, null, null, null, null, null);
    List<ItemView> items = new ArrayList<>(List.of(item));
    var view = new RecommendationView("id", items);
    items.clear();
    assertThat(view.products()).containsExactly(item);
    assertThatThrownBy(() -> view.products().clear()).isInstanceOf(UnsupportedOperationException.class);
    assertThatThrownBy(() -> RecommendationResponse.from(view).products().clear()).isInstanceOf(UnsupportedOperationException.class);
  }

  @ParameterizedTest
  @ValueSource(strings = {"popular", "trending", "related", "complementary"})
  void fourProductRoutesPreserveServiceArgumentsAndWireBody(String name) throws Exception {
    var service = mock(ProductRecommendationService.class);
    var view = new RecommendationView("service-request", List.of(new ItemView(91L, "상품", null, null, null, "이유", "STRATEGY")));
    var controller = new ProductRecommendationController(service);
    RecommendationResponse actual;
    switch (name) {
      case "popular" -> { when(service.popular("DOG", 6)).thenReturn(view); actual = controller.popular("DOG", 6); verify(service).popular("DOG", 6); }
      case "trending" -> { when(service.trending("DOG", 6)).thenReturn(view); actual = controller.trending("DOG", 6); verify(service).trending("DOG", 6); }
      case "related" -> { when(service.related(71L, 6)).thenReturn(view); actual = controller.related(71L, 6); verify(service).related(71L, 6); }
      default -> { when(service.complementary(71L, 6)).thenReturn(view); actual = controller.complementary(71L, 6); verify(service).complementary(71L, 6); }
    }
    assertThat(actual).isEqualTo(frozen(view));
    assertThat(JSON.writeValueAsString(actual)).isEqualTo(JSON.writeValueAsString(frozen(view)));
  }

  // Frozen constructor arguments from both recommendation services at main 04b29c06.
  static RecommendationResponse frozen(RecommendationView view) {
    return new RecommendationResponse(view.requestId(), view.products().stream().map(item -> new RecommendationItem(
        item.productId(), item.name(), item.shortDescription(), item.thumbnailUrl(),
        item.category() == null ? null : new RecommendationItemCategory(item.category().categoryId(), item.category().name(), item.category().slug()),
        item.reason(), item.strategy())).toList());
  }

  private static String nullable(int pattern, int bit, String value) { return (pattern & bit) != 0 ? null : value; }
}
