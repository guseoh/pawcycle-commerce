package com.pawcycle.backend.catalog.admin.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockingDetails;

import com.pawcycle.backend.catalog.admin.application.AdminCatalogMutationService;
import com.pawcycle.backend.catalog.admin.application.AdminCatalogService;
import com.pawcycle.backend.catalog.admin.model.CatalogAdminCommands;
import com.pawcycle.backend.member.application.AuthenticatedMemberPrincipal;
import java.lang.reflect.Method;
import java.lang.reflect.RecordComponent;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import tools.jackson.databind.ObjectMapper;

class CatalogAdminCommandMappingTests {
  private static final ObjectMapper JSON = new ObjectMapper();

  // Independent wire fixtures also pin component order. Every nullable create-field combination
  // and every absent/null/value PATCH-field combination must map without validation or defaults.
  private record Contract(String name, String fields, String values) {
    boolean patch() { return name.endsWith("Patch"); }
    String[] names() { return fields.split(","); }
  }

  static Stream<Arguments> inputs() {
    return Stream.of(
        new Contract("BrandPatch", "name,slug,logoUrl,active,displayOrder", "[\"name\",\"slug\",\"logo\",false,17]"),
        new Contract("ImageCreate", "imageUrl,altText,displayOrder,imageType", "[\"url\",\"alt\",17,\"SUB\"]"),
        new Contract("ImagePatch", "imageUrl,altText,displayOrder,imageType", "[\"url\",\"alt\",17,\"SUB\"]"),
        new Contract("OptionGroupCreate", "name,displayOrder", "[\"group\",17]"),
        new Contract("OptionGroupPatch", "name,displayOrder", "[\"group\",17]"),
        new Contract("OptionValueCreate", "value,displayOrder", "[\"option\",17]"),
        new Contract("OptionValuePatch", "value,displayOrder", "[\"option\",17]"),
        new Contract("SkuOptionValues", "optionValueIds", "[[13,null,7,13]]"),
        new Contract("FacetDefinitionCreate", "key,name", "[\"key\",\"name\"]"),
        new Contract("FacetDefinitionPatch", "key,name", "[\"key\",\"name\"]"),
        new Contract("FacetOptionCreate", "value,displayOrder", "[\"facet\",17]"),
        new Contract("FacetOptionPatch", "value,displayOrder", "[\"facet\",17]"),
        new Contract("CategoryFacetAssign", "displayOrder", "[17]"),
        new Contract("ProductFacetValues", "facetOptionIds", "[[19,null,5,19]]"),
        new Contract("DetailSectionCreate", "title,body,displayOrder,visible", "[\"title\",\"body\",17,false]"),
        new Contract("DetailSectionPatch", "title,body,displayOrder,visible", "[\"title\",\"body\",17,false]"))
        .flatMap(contract -> IntStream.range(0, (int) Math.pow(contract.patch() ? 3 : 2, contract.names().length))
            .mapToObj(pattern -> Arguments.of(contract.name() + ":" + pattern, contract, pattern)));
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("inputs")
  void mapperAndControllerPreserveEveryNullablePresencePattern(
      String label, Contract contract, int pattern) throws Exception {
    Class<?> requestType = Class.forName(getClass().getPackageName() + "." + contract.name() + "Request");
    Class<?> commandType = Class.forName(CatalogAdminCommands.class.getName() + "$" + contract.name() + "Command");
    var wire = JSON.createObjectNode();
    var values = JSON.readTree(contract.values());
    List<String> names = new ArrayList<>();
    List<Object> expected = new ArrayList<>();
    RecordComponent[] components = commandType.getRecordComponents();
    int component = 0;
    for (int field = 0; field < contract.names().length; field++) {
      String name = contract.names()[field];
      int state = pattern % (contract.patch() ? 3 : 2);
      pattern /= contract.patch() ? 3 : 2;
      boolean present = !contract.patch() || state != 0;
      var value = state == (contract.patch() ? 2 : 1) ? values.get(field) : JSON.nullNode();
      if (present) wire.set(name, value);
      names.add(name);
      expected.add(JSON.convertValue(value, JSON.constructType(components[component++].getGenericType())));
      if (contract.patch()) {
        names.add(name + "Present");
        expected.add(present);
        component++;
      }
    }
    assertThat(Arrays.stream(components).map(RecordComponent::getName)).containsExactlyElementsOf(names);
    Object request = JSON.treeToValue(wire, requestType);
    Object command = CatalogAdminCommandMapping.class.getDeclaredMethod("command", requestType).invoke(null, request);
    assertThat(command).isExactlyInstanceOf(commandType);
    for (int index = 0; index < components.length; index++) {
      assertThat(components[index].getAccessor().invoke(command)).as(names.get(index)).isEqualTo(expected.get(index));
    }

    AdminCatalogMutationService mutations = mock(AdminCatalogMutationService.class,
        invocation -> mock(invocation.getMethod().getReturnType()));
    AdminCatalogController controller = new AdminCatalogController(mock(AdminCatalogService.class), mutations);
    Method endpoint = Arrays.stream(AdminCatalogController.class.getDeclaredMethods())
        .filter(method -> Arrays.asList(method.getParameterTypes()).contains(requestType)).findFirst().orElseThrow();
    Object[] arguments = Arrays.stream(endpoint.getParameterTypes()).map(type -> {
      if (type == requestType) return request;
      if (type == AuthenticatedMemberPrincipal.class) return new AuthenticatedMemberPrincipal(7L);
      return 11L;
    }).toArray();
    endpoint.invoke(controller, arguments);
    var calls = mockingDetails(mutations).getInvocations();
    assertThat(calls).hasSize(1);
    var call = calls.iterator().next();
    assertThat(call.getMethod().getName()).isEqualTo(endpoint.getName());
    assertThat(call.getArguments()[0]).isEqualTo(7L);
    assertThat(call.getArguments()[call.getArguments().length - 1]).isEqualTo(command);
  }
}
