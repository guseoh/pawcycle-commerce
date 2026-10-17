package com.pawcycle.backend.catalog.admin.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import com.pawcycle.backend.catalog.admin.application.AdminCatalogMutationService;
import com.pawcycle.backend.catalog.admin.application.AdminCatalogService;
import com.pawcycle.backend.catalog.admin.model.CatalogAdminModels.*;
import com.pawcycle.backend.catalog.product.domain.ProductStatus;
import com.pawcycle.backend.catalog.sku.domain.SkuStatus;
import com.pawcycle.backend.member.application.AuthenticatedMemberPrincipal;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.RecordComponent;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.ResponseEntity;
import tools.jackson.databind.ObjectMapper;

class CatalogAdminCoreMappingTests {
  private static final ObjectMapper JSON = new ObjectMapper();
  private static final List<Class<?>> CREATES = List.of(BrandCreateRequest.class,
      CategoryCreateRequest.class, ProductCreateRequest.class, SkuCreateRequest.class);
  private static final List<Class<?>> PATCHES = List.of(CategoryPatchRequest.class,
      ProductPatchRequest.class, SkuPatchRequest.class);

  static Stream<Arguments> creates() {
    return CREATES.stream().flatMap(type -> IntStream.range(0, 1 << type.getRecordComponents().length)
        .mapToObj(pattern -> Arguments.of(type, pattern)));
  }

  @ParameterizedTest
  @MethodSource("creates")
  void createCommandsPreserveEveryFieldAndNullableCombination(Class<?> type, int pattern) throws Exception {
    Object request = record(type, pattern);
    Object command = command(request);
    assertThat(Arrays.stream(command.getClass().getRecordComponents()).map(RecordComponent::getName))
        .containsExactly(Arrays.stream(type.getRecordComponents()).map(RecordComponent::getName).toArray(String[]::new));
    for (var field : type.getRecordComponents()) {
      assertThat(command.getClass().getMethod(field.getName()).invoke(command))
          .isEqualTo(field.getAccessor().invoke(request));
    }
  }

  static Stream<Arguments> patches() {
    return PATCHES.stream().flatMap(type -> {
      List<Map<String, Object>> cases = new ArrayList<>();
      cases.add(Map.of());
      Map<String, Object> allNull = new LinkedHashMap<>();
      Map<String, Object> allValues = new LinkedHashMap<>();
      Map<String, Object> mixed = new LinkedHashMap<>();
      for (var field : type.getDeclaredFields()) {
        if (field.getType() == boolean.class) continue;
        var explicitNull = new LinkedHashMap<String, Object>();
        explicitNull.put(field.getName(), null);
        cases.add(explicitNull);
        cases.add(Map.of(field.getName(), value(field.getType(), field.getName())));
        allNull.put(field.getName(), null);
        allValues.put(field.getName(), value(field.getType(), field.getName()));
        mixed.put(field.getName(), mixed.size() % 2 == 0 ? null : value(field.getType(), field.getName()));
      }
      cases.add(allNull);
      cases.add(allValues);
      cases.add(mixed);
      return cases.stream().map(fields -> Arguments.of(type, fields));
    });
  }

  @ParameterizedTest
  @MethodSource("patches")
  void patchCommandsPreserveAbsentExplicitNullValueAndMixedFields(
      Class<?> type, Map<String, Object> fields) throws Exception {
    Object request = JSON.readValue(JSON.writeValueAsString(fields), type);
    Object command = command(request);
    for (var field : type.getDeclaredFields()) {
      if (field.getType() == boolean.class) continue;
      String name = field.getName();
      assertThat(command.getClass().getMethod(name + "Present").invoke(command)).isEqualTo(fields.containsKey(name));
      String getter = "get" + Character.toUpperCase(name.charAt(0)) + name.substring(1);
      assertThat(command.getClass().getMethod(name).invoke(command)).isEqualTo(type.getMethod(getter).invoke(request));
    }
  }

  static Stream<Arguments> responses() {
    return Stream.of(ProductView.class, SkuView.class).flatMap(type -> IntStream.range(0,
        1 << (int) Arrays.stream(type.getRecordComponents()).filter(field -> !field.getType().isPrimitive()).count())
        .mapToObj(pattern -> Arguments.of(type, pattern)));
  }

  @ParameterizedTest
  @MethodSource("responses")
  void coreResponsesKeepCanonicalFieldsNullsEnumDecimalScaleAndJson(Class<?> type, int pattern) throws Exception {
    Object view = record(type, pattern);
    Object expected = legacyResponse(view);
    Object actual = type == ProductView.class ? ProductResponse.from((ProductView) view) : SkuResponse.from((SkuView) view);
    assertThat(actual).isEqualTo(expected);
    assertThat(JSON.writeValueAsString(actual)).isEqualTo(JSON.writeValueAsString(expected));
  }

  // Frozen constructor arguments from AdminCatalogService at main 25875fd8.
  static Object legacyResponse(Object view) {
    if (view instanceof BrandView v) return new BrandResponse(v.brandId(), v.name(), v.slug(), v.logoUrl(), v.active(), v.displayOrder());
    if (view instanceof CategoryView v) return new CategoryResponse(v.categoryId(), v.parentId(), v.name(), v.slug(), v.displayOrder(), v.active());
    if (view instanceof ProductView v) return new ProductResponse(v.productId(), v.categoryId(), v.brandId(), v.name(),
        v.shortDescription(), v.description(), v.petType(), v.thumbnailUrl(), v.status());
    SkuView v = (SkuView) view;
    return new SkuResponse(v.skuId(), v.productId(), v.skuCode(), v.name(), v.price(), v.compareAtPrice(),
        v.subscribable(), v.displayOrder(), v.status());
  }

  @ParameterizedTest
  @ValueSource(strings = {"Brand", "Category", "Product", "Sku"})
  void listFactoriesKeepEmptyDuplicateOrderAndNullFailures(String name) throws Exception {
    Class<?> view = Class.forName(BrandView.class.getName().replace("BrandView", name + "View"));
    Method from = Class.forName(BrandListResponse.class.getName().replace("Brand", name)).getMethod("from", List.class);
    Object first = record(view, 0), second = record(view, 1);
    List<?> input = List.of(first, second, first);
    Object result = from.invoke(null, input);
    var component = result.getClass().getRecordComponents()[0];
    assertThat(component.getAccessor().invoke(result)).isEqualTo(input.stream().map(CatalogAdminCoreMappingTests::legacyResponse).toList());
    Object empty = from.invoke(null, List.of());
    assertThat(component.getAccessor().invoke(empty)).isEqualTo(List.of());
    assertThatThrownBy(() -> from.invoke(null, new Object[] {null})).hasCauseInstanceOf(NullPointerException.class);
    assertThatThrownBy(() -> from.invoke(null, Arrays.asList(first, null))).hasCauseInstanceOf(NullPointerException.class);
  }

  @Test
  void compatibilityConstructorsRemainAndCanonicalFactoryKeepsActualBrand() {
    assertThat(new ProductResponse(2L, 3L, "p", "s", null, "DOG", null, ProductStatus.DRAFT).brandId()).isEqualTo(1L);
    assertThat(ProductResponse.from(new ProductView(2L, 3L, 97L, "p", "s", null, "DOG", null, ProductStatus.DRAFT)).brandId()).isEqualTo(97L);
    assertThat(new SkuResponse(2L, 3L, "SKU", "s", new BigDecimal("12.30"), true, 0, SkuStatus.ACTIVE).compareAtPrice()).isNull();
    assertThatThrownBy(() -> ProductResponse.from(null)).isInstanceOf(NullPointerException.class);
    assertThatThrownBy(() -> SkuResponse.from(null)).isInstanceOf(NullPointerException.class);
  }

  @ParameterizedTest
  @ValueSource(strings = {"brands", "createBrand", "categories", "createCategory", "category", "updateCategory",
      "products", "createProduct", "product", "updateProduct", "skus", "createSku", "updateSku"})
  void allThirteenControllerPathsKeepDelegationBodyStatusAndLocation(String name) throws Exception {
    var calls = new ArrayList<Object[]>();
    var results = new ArrayList<Object>();
    org.mockito.stubbing.Answer<Object> answer = call -> {
      calls.add(call.getArguments());
      Class<?> resultType = call.getMethod().getReturnType();
      Object result = resultType == List.class ? List.of(record((Class<?>) ((ParameterizedType)
          call.getMethod().getGenericReturnType()).getActualTypeArguments()[0], 0)) : record(resultType, 0);
      results.add(result);
      return result;
    };
    var controller = new AdminCatalogController(mock(AdminCatalogService.class, answer), mock(AdminCatalogMutationService.class, answer));
    Method method = Arrays.stream(AdminCatalogController.class.getDeclaredMethods()).filter(m -> m.getName().equals(name)).findFirst().orElseThrow();
    Object[] arguments = new Object[method.getParameterCount()];
    Object request = null;
    for (int i = 0; i < arguments.length; i++) {
      Class<?> type = method.getParameterTypes()[i];
      if (type == AuthenticatedMemberPrincipal.class) arguments[i] = new AuthenticatedMemberPrincipal(53L);
      else if (type == Long.class || type == long.class) arguments[i] = 71L + i;
      else arguments[i] = request = type.isRecord() ? record(type, 0) : JSON.readValue("{}", type);
    }
    Object response = method.invoke(controller, arguments);
    assertThat(calls).hasSize(1);
    if (request != null) {
      var forwarded = calls.getFirst();
      assertThat(forwarded[0]).isEqualTo(53L);
      assertThat(forwarded[forwarded.length - 1]).isEqualTo(command(request));
      for (int i = 1; i < arguments.length - 1; i++) assertThat(forwarded[i]).isEqualTo(arguments[i]);
    }
    Object expected;
    if (results.getFirst() instanceof List<?> values) {
      List<?> mapped = values.stream().map(CatalogAdminCoreMappingTests::legacyResponse).toList();
      expected = method.getReturnType().getDeclaredConstructor(List.class).newInstance(mapped);
    } else expected = legacyResponse(results.getFirst());
    if (response instanceof ResponseEntity<?> entity) {
      assertThat(entity.getStatusCode().value()).isEqualTo(201);
      Object body = entity.getBody();
      assertThat(body).isEqualTo(expected);
      long id = (Long) body.getClass().getRecordComponents()[0].getAccessor().invoke(body);
      String path = switch (name) {
        case "createBrand" -> "/api/admin/brands/";
        case "createCategory" -> "/api/admin/categories/";
        case "createProduct" -> "/api/admin/products/";
        default -> "/api/admin/products/" + arguments[1] + "/skus/";
      };
      assertThat(entity.getHeaders().getLocation().toString()).isEqualTo(path + id);
    } else assertThat(response).isEqualTo(expected);
    assertThat(JSON.writeValueAsString(response instanceof ResponseEntity<?> e ? e.getBody() : response))
        .isEqualTo(JSON.writeValueAsString(expected));
  }

  private static Object command(Object request) throws Exception {
    return CatalogAdminCommandMapping.class.getDeclaredMethod("command", request.getClass()).invoke(null, request);
  }

  private static Object record(Class<?> type, int pattern) throws Exception {
    var fields = type.getRecordComponents();
    Object[] args = new Object[fields.length];
    int bit = 0;
    for (int i = 0; i < fields.length; i++) {
      var field = fields[i];
      args[i] = !field.getType().isPrimitive() && (pattern & (1 << bit++)) != 0 ? null : value(field.getType(), field.getName());
    }
    return type.getDeclaredConstructor(Arrays.stream(fields).map(RecordComponent::getType).toArray(Class<?>[]::new)).newInstance(args);
  }

  private static Object value(Class<?> type, String name) {
    if (type == String.class) return name + "-한글";
    if (type == Long.class) return name.equals("brandId") ? 97L : 41L;
    if (type == Integer.class || type == int.class) return 7;
    if (type == Boolean.class || type == boolean.class) return false;
    if (type == BigDecimal.class) return new BigDecimal(name.equals("price") ? "12345.60" : "23456.70");
    if (type == ProductStatus.class) return ProductStatus.DRAFT;
    if (type == SkuStatus.class) return SkuStatus.ACTIVE;
    throw new AssertionError(type);
  }
}
