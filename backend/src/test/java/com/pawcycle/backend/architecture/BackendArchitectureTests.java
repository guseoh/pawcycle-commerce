package com.pawcycle.backend.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Set;
import java.util.TreeSet;
import javax.tools.ToolProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class BackendArchitectureTests {
  private static final String ROOT = "com.pawcycle.backend.";
  private static final Set<String> LAYERED_ROOTS = Set.of(
      ROOT + "commerce", ROOT + "subscription", ROOT + "recommendation", ROOT + "interaction");

  @Test
  void productionDependenciesMustNotAddLegacyViolations() throws Exception {
    // Import production output only: test fixtures and dependency jars are not rule origins.
    JavaClasses classes = new ClassFileImporter().importPath(Path.of("build/classes/java/main"));
    assertThat(classes).isNotEmpty();
    assertThat(classes.stream()
        .filter(type -> LAYERED_ROOTS.contains(type.getPackageName()))
        .map(JavaClass::getName).toList())
        .as("Normalized runtime production types must belong to a feature/layer package").isEmpty();
    Set<String> actual = violations(classes);
    // Diagnostics never update the checked-in baseline, including on CI.
    Path report = Path.of("build/reports/architecture/current-violations.txt");
    Files.createDirectories(report.getParent());
    Files.write(report, actual);
    Set<String> baseline = new TreeSet<>(Files.readAllLines(
        Path.of("src/test/resources/architecture/legacy-dependencies.txt")));
    baseline.removeIf(line -> line.isBlank() || line.startsWith("#"));
    Set<String> added = new TreeSet<>(actual);
    added.removeAll(baseline);
    Set<String> removed = new TreeSet<>(baseline);
    removed.removeAll(actual);
    assertThat(added).as("New architecture violations; see %s", report).isEmpty();
    assertThat(removed).as("Remove resolved entries from the baseline").isEmpty();
  }

  private static Set<String> violations(JavaClasses classes) {
    Set<String> actual = new TreeSet<>();
    for (JavaClass origin : classes) {
      if (!origin.getName().startsWith(ROOT) || generatedQueryType(origin)) continue;
      for (var dependency : origin.getDirectDependenciesFromSelf()) {
        JavaClass target = dependency.getTargetClass();
        for (String rule : rules(origin.getName(), target.getName(), target.isAnnotatedWith("jakarta.persistence.Entity"))) {
          actual.add(rule + " | " + origin.getName() + " -> " + target.getName());
        }
        if (!inLayer(origin.getName(), "persistence") && generatedQueryType(target)) {
          actual.add("querydsl-boundary | " + origin.getName() + " -> " + target.getName());
        }
      }
    }
    return actual;
  }

  private static boolean generatedQueryType(JavaClass type) {
    return type.getSimpleName().startsWith("Q")
        && type.isAssignableTo("com.querydsl.core.types.dsl.BeanPath");
  }

  static Set<String> rules(String origin, String target, boolean targetEntity) {
    Set<String> result = new TreeSet<>();
    boolean internal = target.startsWith(ROOT);
    if (inLayer(origin, "application")) {
      if (target.equals("org.springframework.jdbc.core.JdbcTemplate")
          || target.equals("jakarta.persistence.EntityManager")) result.add("application-sql");
      if (internal && inLayer(target, "api")) result.add("application-api");
    }
    if (inLayer(origin, "domain") && internal
        && (inLayer(target, "api") || inLayer(target, "infrastructure"))) result.add("domain-adapter");
    if (inLayer(origin, "api")) {
      if (internal && inLayer(target, "persistence")) result.add("api-persistence");
      // Stronger than Entity return-only: also catches generic Entity return types and fields.
      if (targetEntity || target.equals("jakarta.persistence.EntityManager")
          || target.equals("org.springframework.jdbc.core.JdbcTemplate")) result.add("api-storage");
    }
    if (!isolated(origin) && internal && isolated(target)) result.add("runtime-maintenance");
    // Q-types and Querydsl stay inside persistence; generated Q-types are excluded as origins.
    if (!inLayer(origin, "persistence") && target.startsWith("com.querydsl.")) result.add("querydsl-boundary");
    return result;
  }

  private static boolean inLayer(String name, String layer) {
    return Arrays.asList(name.split("\\.")).contains(layer);
  }

  private static boolean isolated(String name) {
    return inLayer(name, "maintenance") || inLayer(name, "performance")
        || inLayer(name, "migration") || inLayer(name, "bootstrap");
  }

  @Test
  void detectsEntityHiddenInsideGenericControllerReturnType(@TempDir Path fixture) throws Exception {
    Path entity = fixture.resolve("StoredEntity.java");
    Path controller = fixture.resolve("Controller.java");
    Files.writeString(entity, "package com.pawcycle.backend.probe.domain; "
        + "@jakarta.persistence.Entity public class StoredEntity {}");
    Files.writeString(controller, "package com.pawcycle.backend.probe.api; "
        + "public class Controller { public java.util.List<com.pawcycle.backend.probe.domain.StoredEntity> read() { return null; } }");
    Path annotationJar = Path.of(jakarta.persistence.Entity.class.getProtectionDomain().getCodeSource().getLocation().toURI());
    assertThat(ToolProvider.getSystemJavaCompiler().run(null, null, null,
        "-proc:none", "-classpath", annotationJar.toString(), "-d", fixture.toString(),
        entity.toString(), controller.toString())).isZero();
    assertThat(violations(new ClassFileImporter().importPath(fixture)))
        .containsExactly("api-storage | com.pawcycle.backend.probe.api.Controller -> com.pawcycle.backend.probe.domain.StoredEntity");
  }

  @Test
  void rejectsEveryForbiddenBoundaryAndAllowsPersistenceAndIsolatedTools() {
    assertThat(rules(ROOT + "example.application.UseCase", "org.springframework.jdbc.core.JdbcTemplate", false))
        .containsExactly("application-sql");
    assertThat(rules(ROOT + "example.application.UseCase", "jakarta.persistence.EntityManager", false))
        .containsExactly("application-sql");
    assertThat(rules(ROOT + "example.application.UseCase", ROOT + "example.api.Dto", false))
        .containsExactly("application-api");
    assertThat(rules(ROOT + "example.domain.Model", ROOT + "example.api.Dto", false))
        .containsExactly("domain-adapter");
    assertThat(rules(ROOT + "example.domain.Model", ROOT + "example.infrastructure.Provider", false))
        .containsExactly("domain-adapter");
    assertThat(rules(ROOT + "example.api.Controller", ROOT + "example.persistence.Store", false))
        .containsExactly("api-persistence");
    assertThat(rules(ROOT + "example.api.Controller", ROOT + "example.domain.Entity", true))
        .containsExactly("api-storage");
    assertThat(rules(ROOT + "example.application.UseCase", ROOT + "example.performance.Harness", false))
        .containsExactly("runtime-maintenance");
    assertThat(rules(ROOT + "example.application.UseCase", "com.querydsl.jpa.impl.JPAQueryFactory", false))
        .containsExactly("querydsl-boundary");
    assertThat(rules(ROOT + "example.persistence.Store", "jakarta.persistence.EntityManager", false)).isEmpty();
    assertThat(rules(ROOT + "example.migration.Tool", ROOT + "example.performance.Harness", false)).isEmpty();
  }
}
