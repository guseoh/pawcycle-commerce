package com.pawcycle.backend.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import com.tngtech.archunit.core.importer.ClassFileImporter;
import java.lang.reflect.GenericArrayType;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import org.junit.jupiter.api.Test;
import org.springframework.core.BridgeMethodResolver;

class PersistenceResultBoundaryTests {
  private static final String ROOT = "com.pawcycle.backend.";

  @Test
  void applicationAndApiCallsReceiveTypedPersistenceResults() {
    var classes = new ClassFileImporter().importPath(Path.of("build/classes/java/main"));
    Set<String> violations = new TreeSet<>();
    int checked = 0;
    for (var caller : classes) {
      if (!layer(caller.getName(), "application") && !layer(caller.getName(), "api")) continue;
      for (var call : caller.getMethodCallsFromSelf()) {
        String owner = call.getTargetOwner().getName();
        if (!owner.startsWith(ROOT) || !layer(owner, "persistence") || isolated(owner)) continue;
        var method = call.getTarget().resolveMember();
        assertThat(method).as("Resolve persistence boundary %s", call.getDescription()).isPresent();
        var declaration = BridgeMethodResolver.findBridgedMethod(method.orElseThrow().reflect());
        // Inherited framework CRUD contracts are entity generics, not handwritten SQL rows.
        // Record accessors are checked with their concrete type arguments at the producing method.
        if (!declaration.getDeclaringClass().getName().startsWith(ROOT)
            || declaration.getDeclaringClass().isRecord()) continue;
        checked++;
        if (rawResult(declaration.getGenericReturnType(), Map.of(), new HashSet<>())) {
          violations.add(call.getDescription());
        }
      }
    }
    assertThat(checked).as("Production boundary calls must actually be inspected").isPositive();
    assertThat(violations).as("Raw DB results escaping persistence").isEmpty();
  }

  @Test
  void rejectsRawResultsIncludingNestedRecordsAndGenericContainers() throws Exception {
    for (String name : List.of("row", "rows", "tuple", "querydslTuple", "nested", "generic")) {
      Type result = Samples.class.getDeclaredMethod(name).getGenericReturnType();
      assertThat(rawResult(result, Map.of(), new HashSet<>())).as(name).isTrue();
    }
  }

  @Test
  void allowsTypedMapsAndViewsWithoutInspectingLocalSqlOrJsonInputs() throws Exception {
    for (String name : List.of("scores", "views", "page", "nothing")) {
      Type result = Samples.class.getDeclaredMethod(name).getGenericReturnType();
      assertThat(rawResult(result, Map.of(), new HashSet<>())).as(name).isFalse();
    }
    var bridge = VisibleRepository.class.getMethod("views");
    var declaration = BridgeMethodResolver.findBridgedMethod(bridge);
    assertThat(declaration.getDeclaringClass()).isEqualTo(HiddenQueries.class);
    assertThat(rawResult(declaration.getGenericReturnType(), Map.of(), new HashSet<>())).isFalse();
  }

  private static boolean rawResult(Type type, Map<TypeVariable<?>, Type> bindings, Set<Type> visiting) {
    if (type instanceof TypeVariable<?> variable) {
      Type actual = bindings.get(variable);
      return actual == null || rawResult(actual, bindings, visiting);
    }
    if (!visiting.add(type)) return false;
    try {
      if (type instanceof GenericArrayType array) {
        return rawResult(array.getGenericComponentType(), bindings, visiting);
      }
      Class<?> raw;
      Map<TypeVariable<?>, Type> resolved = new HashMap<>(bindings);
      if (type instanceof ParameterizedType parameterized) {
        raw = (Class<?>) parameterized.getRawType();
        Type[] arguments = parameterized.getActualTypeArguments();
        for (Type argument : arguments) {
          if (rawResult(argument, bindings, visiting)) return true;
        }
        for (int i = 0; i < arguments.length; i++) resolved.put(raw.getTypeParameters()[i], arguments[i]);
      } else if (type instanceof Class<?> concrete) {
        raw = concrete;
      } else {
        return true; // Wildcard/unresolved result shapes require a typed boundary.
      }
      if (raw == Object.class || raw == jakarta.persistence.Tuple.class
          || raw == com.querydsl.core.Tuple.class) return true;
      if (raw.isArray()) return rawResult(raw.getComponentType(), bindings, visiting);
      if ((Map.class.isAssignableFrom(raw) || Iterable.class.isAssignableFrom(raw))
          && !(type instanceof ParameterizedType)) return true;
      if (raw.isRecord() && raw.getName().startsWith(ROOT)) {
        for (var component : raw.getRecordComponents()) {
          if (rawResult(component.getGenericType(), resolved, visiting)) return true;
        }
      }
      return false;
    } finally {
      visiting.remove(type);
    }
  }

  private static boolean layer(String name, String layer) {
    return name.contains("." + layer + ".");
  }

  private static boolean isolated(String name) {
    return layer(name, "maintenance") || layer(name, "bootstrap")
        || layer(name, "migration") || layer(name, "performance");
  }

  private record RawView(Map<String, Object> row) {}
  private record Page<T>(List<T> items) {}
  private record View(long id, String name) {}

  static class HiddenQueries {
    public List<View> views() { return List.of(); }
  }

  public static class VisibleRepository extends HiddenQueries {}

  private interface Samples {
    Object[] row();
    List<Map<String, Object>> rows();
    jakarta.persistence.Tuple tuple();
    com.querydsl.core.Tuple querydslTuple();
    List<RawView> nested();
    Page<RawView> generic();
    Map<Long, Long> scores();
    Map<Long, List<View>> views();
    Page<View> page();
    void nothing();
  }
}
