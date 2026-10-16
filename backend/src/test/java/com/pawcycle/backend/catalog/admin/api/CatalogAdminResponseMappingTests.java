package com.pawcycle.backend.catalog.admin.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import com.pawcycle.backend.catalog.admin.application.AdminCatalogMutationService;
import com.pawcycle.backend.catalog.admin.application.AdminCatalogService;
import com.pawcycle.backend.catalog.admin.model.CatalogAdminModels;
import com.pawcycle.backend.member.application.AuthenticatedMemberPrincipal;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.RecordComponent;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.ResponseEntity;
import tools.jackson.databind.ObjectMapper;

class CatalogAdminResponseMappingTests {
  private static final ObjectMapper JSON = new ObjectMapper();

  static Stream<Arguments> views() {
    return Arrays.stream(CatalogAdminModels.class.getDeclaredClasses()).flatMap(type -> {
      int nullableFields = (int) Arrays.stream(type.getRecordComponents())
          .filter(component -> !component.getType().isPrimitive() && component.getType() != List.class).count();
      return IntStream.range(0, 1 << nullableFields).boxed().flatMap(pattern -> Stream.of(false, true)
          .map(empty -> Arguments.of(type.getSimpleName() + ":" + pattern + ":empty=" + empty,
              type, pattern, empty)));
    });
  }

  private static Object fixture(Class<?> type, int marker, int pattern, boolean empty) throws Exception {
    RecordComponent[] components = type.getRecordComponents();
    Object[] values = new Object[components.length];
    int nullable = 0;
    for (int index = 0; index < components.length; index++) {
      var component = components[index];
      Class<?> field = component.getType();
      if (field == int.class) values[index] = marker + index;
      else if (field == boolean.class) values[index] = marker % 2 == 0;
      else if (field == List.class) {
        Class<?> element = (Class<?>) ((ParameterizedType) component.getGenericType()).getActualTypeArguments()[0];
        values[index] = empty ? List.of() : element == Long.class
            ? Arrays.asList(19L, null, 5L, 19L)
            : List.of(fixture(element, marker + 9, pattern, false), fixture(element, marker - 3, pattern, true));
      } else if ((pattern & (1 << nullable++)) != 0) values[index] = null;
      else if (field == Long.class) values[index] = (long) marker + index;
      else if (field == String.class) values[index] = component.getName() + "-" + marker;
      else if (field == Instant.class) values[index] = Instant.parse("2026-10-17T00:01:02.123456Z").plusSeconds(index);
      else throw new AssertionError("Unexpected View field: " + component);
    }
    return type.getDeclaredConstructor(Arrays.stream(components).map(RecordComponent::getType)
        .toArray(Class<?>[]::new)).newInstance(values);
  }

  private static Object map(Class<?> owner, Object view) throws Exception {
    return owner.getDeclaredMethod("response", view.getClass()).invoke(null, view);
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("views")
  void allSixteenMappingsMatchFrozenValuesTypesNullsOrderAndJson(
      String label, Class<?> type, int pattern, boolean empty) throws Exception {
    Object view = fixture(type, 101, pattern, empty);
    Object expected = map(FrozenCatalogAdminResponseMapping.class, view);
    Object actual = map(CatalogAdminResponseMapping.class, view);
    assertThat(actual).isExactlyInstanceOf(expected.getClass()).isEqualTo(expected);
    assertThat(JSON.writeValueAsString(actual)).isEqualTo(JSON.writeValueAsString(expected));
  }

  static Stream<Method> mappings() {
    return Arrays.stream(CatalogAdminResponseMapping.class.getDeclaredMethods());
  }

  static Stream<Arguments> invalidLists() {
    return Arrays.stream(CatalogAdminModels.class.getDeclaredClasses())
        .filter(type -> Arrays.stream(type.getRecordComponents()).anyMatch(field -> field.getType() == List.class))
        .flatMap(type -> Stream.of(Arguments.of(type, false), Arguments.of(type, true)));
  }

  @ParameterizedTest
  @MethodSource("invalidLists")
  void nullListsAndNestedElementsKeepTheFrozenOutcome(Class<?> type, boolean nullElement) throws Exception {
    Object valid = fixture(type, 101, 0, false);
    var components = type.getRecordComponents();
    Object[] arguments = new Object[components.length];
    for (int index = 0; index < components.length; index++) {
      arguments[index] = components[index].getType() == List.class
          ? nullElement ? Arrays.asList((Object) null) : null
          : components[index].getAccessor().invoke(valid);
    }
    Object view = type.getDeclaredConstructor(Arrays.stream(components).map(RecordComponent::getType)
        .toArray(Class<?>[]::new)).newInstance(arguments);
    Object expected;
    try {
      expected = map(FrozenCatalogAdminResponseMapping.class, view);
    } catch (InvocationTargetException legacyFailure) {
      assertThatThrownBy(() -> map(CatalogAdminResponseMapping.class, view))
          .isInstanceOf(InvocationTargetException.class).hasCauseExactlyInstanceOf(legacyFailure.getCause().getClass());
      return;
    }
    assertThat(map(CatalogAdminResponseMapping.class, view)).isEqualTo(expected);
  }

  @ParameterizedTest
  @MethodSource("mappings")
  void nullRootStillFailsLikeFrozenMapper(Method mapping) {
    assertThatThrownBy(() -> mapping.invoke(null, new Object[] {null}))
        .isInstanceOf(InvocationTargetException.class).hasCauseInstanceOf(NullPointerException.class);
    assertThatThrownBy(() -> FrozenCatalogAdminResponseMapping.class
        .getDeclaredMethod("response", mapping.getParameterTypes()).invoke(null, new Object[] {null}))
        .isInstanceOf(InvocationTargetException.class).hasCauseInstanceOf(NullPointerException.class);
  }

  @ParameterizedTest
  @ValueSource(strings = {"updateBrand", "createImage", "updateImage", "createOptionGroup",
      "updateOptionGroup", "createOptionValue", "updateOptionValue", "setSkuOptionValues",
      "createFacetDefinition", "updateFacetDefinition", "createFacetOption", "updateFacetOption",
      "assignCategoryFacet", "setProductFacetValues", "createDetailSection", "updateDetailSection",
      "brand", "images", "optionGroups", "skuOptionValues", "facetDefinitions", "facetDefinition",
      "productFacetValues", "categoryFacets", "detailSections"})
  void controllerKeepsBodyStatusAndCreationLocationForAllExpansionPaths(String name) throws Exception {
    Method endpoint = Arrays.stream(AdminCatalogController.class.getDeclaredMethods())
        .filter(method -> method.getName().equals(name)).findFirst().orElseThrow();
    Method useCase = Arrays.stream(AdminCatalogMutationService.class.getDeclaredMethods())
        .filter(method -> method.getName().equals(name)).findFirst().orElseThrow();
    Object view = fixture(useCase.getReturnType(), 101, 0, false);
    var mutations = mock(AdminCatalogMutationService.class, invocation -> view);
    var controller = new AdminCatalogController(mock(AdminCatalogService.class), mutations);
    Object[] arguments = Arrays.stream(endpoint.getParameterTypes()).map(type -> {
      if (type == AuthenticatedMemberPrincipal.class) return new AuthenticatedMemberPrincipal(7L);
      if (type == long.class || type == Long.class) return 11L;
      return JSON.readValue("{}", type);
    }).toArray();
    Object returned = endpoint.invoke(controller, arguments);
    Object expected = map(FrozenCatalogAdminResponseMapping.class, view);
    var paths = Map.of("createImage", "/api/admin/products/11/images/", "createOptionGroup",
        "/api/admin/products/11/option-groups/", "createOptionValue", "/api/admin/products/11/option-groups/11/values/",
        "createFacetDefinition", "/api/admin/facets/", "createFacetOption", "/api/admin/facets/11/options/",
        "createDetailSection", "/api/admin/products/11/detail-sections/");
    if (paths.containsKey(name)) {
      var response = (ResponseEntity<?>) returned;
      assertThat(response.getStatusCode().value()).isEqualTo(201);
      assertThat(response.getHeaders().getLocation().toString()).isEqualTo(paths.get(name) + 101);
      returned = response.getBody();
    }
    assertThat(returned).isEqualTo(expected);
    assertThat(JSON.writeValueAsString(returned)).isEqualTo(JSON.writeValueAsString(expected));
  }
}
