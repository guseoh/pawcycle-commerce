package com.pawcycle.backend.catalog.engagement.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import com.pawcycle.backend.catalog.engagement.application.ProductEngagementService;
import com.pawcycle.backend.catalog.engagement.model.PageView;
import com.pawcycle.backend.catalog.engagement.model.ProductEngagementViews.*;
import com.pawcycle.backend.member.application.AuthenticatedMemberPrincipal;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.RecordComponent;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.http.ResponseEntity;
import tools.jackson.databind.ObjectMapper;

class EngagementResponseBoundaryTests {
  private static final ObjectMapper JSON = new ObjectMapper();
  private static final List<Class<?>> VIEWS = List.of(ReviewView.class, AdminReviewView.class,
      QuestionView.class, AdminQuestionView.class);

  static Stream<Arguments> views() {
    return VIEWS.stream().flatMap(type -> IntStream.range(0, 1 << (int) Arrays.stream(type.getRecordComponents())
        .filter(field -> !field.getType().isPrimitive()).count()).mapToObj(pattern -> Arguments.of(type, pattern)));
  }

  @ParameterizedTest
  @MethodSource("views")
  void scalarFactoriesMatchFrozenFieldsNullsAndMicrosecondInstantJson(Class<?> type, int pattern) throws Exception {
    Object view = fixture(type, pattern);
    Object expected = frozen(view);
    Object actual = response(type).getMethod("from", type).invoke(null, view);
    assertThat(actual).isEqualTo(expected);
    assertThat(JSON.writeValueAsString(actual)).isEqualTo(JSON.writeValueAsString(expected));
  }

  static Stream<Class<?>> types() { return VIEWS.stream(); }

  @ParameterizedTest
  @MethodSource("types")
  void nullableLegacyHelperAndPublicConstructorRemainCompatible(Class<?> type) throws Exception {
    assertThat(response(type).getMethod("from", type).invoke(null, new Object[] {null})).isNull();
    assertThat(FrozenEngagementResponseMapping.class.getDeclaredMethod("response", type)
        .invoke(null, new Object[] {null})).isNull();
    Class<?> listType = Class.forName(response(type).getName().replace("Response", "ListResponse"));
    var legacyNullList = listType.getDeclaredConstructor(List.class, int.class, int.class, long.class, int.class)
        .newInstance(null, 0, 20, 0L, 0);
    assertThat(listType.getMethod("items").invoke(legacyNullList)).isNull();
  }

  @ParameterizedTest
  @MethodSource("types")
  void pagesPreserveEmptyNullElementsDuplicatesOrderAndAllMetadata(Class<?> type) throws Exception {
    Object first = fixture(type, 0), second = fixture(type, 1);
    Class<?> listType = Class.forName(response(type).getName().replace("Response", "ListResponse"));
    for (List<?> items : List.of(List.of(), Arrays.asList(first, null, second, first))) {
      for (long total : new long[] {0, 21, Long.MAX_VALUE}) {
        var page = new PageView<>(items, Integer.MAX_VALUE, 100, total, (int) Math.ceil((double) total / 100));
        List<Object> expectedItems = new ArrayList<>();
        for (Object item : items) expectedItems.add(item == null ? null : frozen(item));
        Object expected = listType.getDeclaredConstructor(List.class, int.class, int.class, long.class, int.class)
            .newInstance(expectedItems, page.page(), page.size(), page.totalElements(), page.totalPages());
        Object actual = listType.getMethod("from", PageView.class).invoke(null, page);
        assertThat(actual).isEqualTo(expected);
        assertThat(JSON.writeValueAsString(actual)).isEqualTo(JSON.writeValueAsString(expected));
        @SuppressWarnings("unchecked") var mapped = (List<Object>) listType.getMethod("items").invoke(actual);
        assertThatThrownBy(() -> mapped.add(first)).isInstanceOf(UnsupportedOperationException.class);
      }
    }
  }

  static Stream<Arguments> routes() {
    return Stream.of(
        Arguments.of(ProductReviewController.class, "reviews"), Arguments.of(ProductReviewController.class, "myReview"),
        Arguments.of(ProductReviewController.class, "create"), Arguments.of(ProductReviewController.class, "update"),
        Arguments.of(ProductQuestionController.class, "questions"), Arguments.of(ProductQuestionController.class, "create"),
        Arguments.of(ProductQuestionController.class, "update"), Arguments.of(AdminProductEngagementController.class, "reviews"),
        Arguments.of(AdminProductEngagementController.class, "questions"), Arguments.of(AdminProductEngagementController.class, "answer"));
  }

  @ParameterizedTest
  @MethodSource("routes")
  void tenResultPathsKeepBodiesMetadataStatusAndCreationLocation(Class<?> owner, String name) throws Exception {
    List<Object> results = new ArrayList<>();
    var service = mock(ProductEngagementService.class, call -> {
      Class<?> type = call.getMethod().getReturnType();
      Object result = type == PageView.class ? new PageView<>(List.of(fixture((Class<?>) ((ParameterizedType)
          call.getMethod().getGenericReturnType()).getActualTypeArguments()[0], 0)), 3, 20, 61, 4) : fixture(type, 0);
      results.add(result);
      return result;
    });
    Object controller = owner.getDeclaredConstructor(ProductEngagementService.class).newInstance(service);
    Method method = Arrays.stream(owner.getDeclaredMethods()).filter(m -> m.getName().equals(name)).findFirst().orElseThrow();
    Object[] arguments = new Object[method.getParameterCount()];
    for (int i = 0; i < arguments.length; i++) {
      Class<?> type = method.getParameterTypes()[i];
      arguments[i] = type == long.class || type == Long.class ? 71L + i : type == int.class ? i + 1
          : type == AuthenticatedMemberPrincipal.class ? new AuthenticatedMemberPrincipal(53L)
          : type.isRecord() ? fixture(type, 0) : JSON.readValue("{}", type);
    }
    Object actual = method.invoke(controller, arguments);
    assertThat(results).hasSize(1);
    if (actual instanceof ResponseEntity<?> entity) {
      assertThat(entity.getStatusCode().value()).isEqualTo(201);
      assertThat(entity.getBody()).isEqualTo(frozen(results.getFirst()));
      String prefix = owner == ProductReviewController.class ? "/api/reviews/" : "/api/product-questions/";
      assertThat(entity.getHeaders().getLocation().toString()).isEqualTo(prefix + 41L);
    } else if (results.getFirst() instanceof PageView<?> page) {
      List<Object> expected = new ArrayList<>();
      for (Object view : page.items()) expected.add(frozen(view));
      Object legacy = method.getReturnType().getDeclaredConstructor(List.class, int.class, int.class, long.class, int.class)
          .newInstance(expected, page.page(), page.size(), page.totalElements(), page.totalPages());
      assertThat(actual).isEqualTo(legacy);
    } else assertThat(actual).isEqualTo(frozen(results.getFirst()));
  }

  static Object frozen(Object view) throws Exception {
    return FrozenEngagementResponseMapping.class.getDeclaredMethod("response", view.getClass()).invoke(null, view);
  }

  private static Class<?> response(Class<?> view) throws Exception {
    return Class.forName(ReviewResponse.class.getPackageName() + "." + view.getSimpleName().replace("View", "Response"));
  }

  private static Object fixture(Class<?> type, int pattern) throws Exception {
    var fields = type.getRecordComponents();
    Object[] values = new Object[fields.length];
    int bit = 0;
    for (int i = 0; i < fields.length; i++) {
      Class<?> field = fields[i].getType();
      values[i] = !field.isPrimitive() && (pattern & (1 << bit++)) != 0 ? null
          : field == Long.class ? 41L : field == Integer.class || field == int.class ? 5
          : field == boolean.class ? false : field == String.class ? fields[i].getName() + "-한글"
          : Instant.parse("2026-10-17T14:59:59.123456Z");
    }
    return type.getDeclaredConstructor(Arrays.stream(fields).map(RecordComponent::getType).toArray(Class<?>[]::new)).newInstance(values);
  }
}
