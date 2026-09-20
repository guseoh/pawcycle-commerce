package com.pawcycle.backend.logging;

import static org.assertj.core.api.Assertions.assertThat;

import com.sun.source.tree.ClassTree;
import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.tree.IdentifierTree;
import com.sun.source.tree.LiteralTree;
import com.sun.source.tree.MemberSelectTree;
import com.sun.source.tree.MethodInvocationTree;
import com.sun.source.tree.VariableTree;
import com.sun.source.util.JavacTask;
import com.sun.source.util.TreeScanner;
import com.sun.source.util.Trees;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Stream;
import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.SimpleJavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;
import org.junit.jupiter.api.Test;

class SensitiveLoggingContractTests {

  private static final Set<String> LOG_LEVELS = Set.of("trace", "debug", "info", "warn", "error");
  private static final List<SensitiveName> SENSITIVE_NAMES =
      List.of(
          new SensitiveName("password hash", "passwordhash"),
          new SensitiveName("password", "password"),
          new SensitiveName("credential", "credential"),
          new SensitiveName("email", "email"),
          new SensitiveName("recipient name", "recipientname"),
          new SensitiveName("recipient phone", "recipientphone"),
          new SensitiveName("phone", "phone"),
          new SensitiveName("postal code", "postalcode"),
          new SensitiveName("address line", "addressline"),
          new SensitiveName("prepare token", "preparetoken"),
          new SensitiveName("auth key", "authkey"),
          new SensitiveName("billing key", "billingkey"),
          new SensitiveName("payment key", "paymentkey"),
          new SensitiveName("customer key", "customerkey"),
          new SensitiveName("session id", "sessionid"),
          new SensitiveName("CSRF token", "csrf"),
          new SensitiveName("Authorization header", "authorization"),
          new SensitiveName("Cookie header", "cookie"));
  private static final Set<String> RAW_REQUEST_VALUE_NAMES =
      Set.of("body", "payload", "requestbody", "rawbody", "getbody", "getpayload");
  private static final Set<String> RAW_REQUEST_OBJECT_NAMES =
      Set.of("request", "httprequest", "servletrequest");

  @Test
  void productionLoggerArgumentsDoNotExposeSensitiveValues() throws Exception {
    Path sourceRoot = productionSourceRoot();
    List<SourceText> productionSources = new ArrayList<>();
    try (Stream<Path> sources = Files.walk(sourceRoot)) {
      for (Path source : sources.filter(path -> path.toString().endsWith(".java")).sorted().toList()) {
        productionSources.add(new SourceText(source.toString(), Files.readString(source)));
      }
    }

    assertThat(findViolations(productionSources)).isEmpty();
  }

  @Test
  void detectsEachSensitiveCategoryAtTheLoggerArgumentBoundary() throws Exception {
    List<ContractCase> cases =
        List.of(
            new ContractCase("password hash", "passwordHash"),
            new ContractCase("password", "password"),
            new ContractCase("credential", "credentials"),
            new ContractCase("email", "email"),
            new ContractCase("recipient name", "recipientName"),
            new ContractCase("recipient phone", "recipientPhone"),
            new ContractCase("phone", "phone"),
            new ContractCase("postal code", "postalCode"),
            new ContractCase("address line", "addressLine1"),
            new ContractCase("prepare token", "prepareToken"),
            new ContractCase("auth key", "authKey"),
            new ContractCase("billing key", "billingKey"),
            new ContractCase("payment key", "paymentKey"),
            new ContractCase("customer key", "customerKey"),
            new ContractCase("session id", "JSESSIONID"),
            new ContractCase("CSRF token", "csrfToken"),
            new ContractCase("Authorization header", "headers.getHeader(\"Authorization\")"),
            new ContractCase("Cookie header", "headers.getHeader(\"Cookie\")"),
            new ContractCase("raw request body", "requestBody"),
            new ContractCase("raw request body", "request"));

    for (ContractCase contractCase : cases) {
      String source =
          "class ContractFixture { Logger log; void verify() { this.log.info(\"event={}\", "
              + contractCase.expression()
              + "); } }";

      assertThat(findViolations("ContractFixture.java", source))
          .as("logger argument category %s", contractCase.category())
          .extracting(Violation::category)
          .contains(contractCase.category());
    }
  }

  @Test
  void ignoresSensitiveNamesOutsideLoggerCallsAndKeepsApprovedIdentifiersAndSafeRequestProjection()
      throws Exception {
    String source =
        """
        class ContractFixture {
          Logger log;
          void verify(
              String password,
              long memberId,
              long subscriptionId,
              long scheduleId,
              Request request,
              RuntimeException exception) {
            consume(password);
            log.info("Password validation completed");
            log.error("Automation failed memberId={} subscriptionId={} scheduleId={} method={}",
                memberId, subscriptionId, scheduleId, request.getMethod(), exception);
          }
        }
        """;

    assertThat(findViolations("ContractFixture.java", source)).isEmpty();
  }

  @Test
  void rejectsRawRequestObjectsAndBodyAccessButAllowsSafeRequestMetadata() throws Exception {
    String rawRequest =
        "class ContractFixture { Logger log; void verify(HttpServletRequest req) { "
            + "log.info(\"request={}\", req); } }";
    String rawBody =
        "class ContractFixture { Logger log; void verify(HttpServletRequest req) { "
            + "log.info(\"body={}\", req.getBody()); } }";
    String safeMetadata =
        "class ContractFixture { Logger log; void verify(HttpServletRequest req) { "
            + "log.info(\"method={}\", req.getMethod()); } }";

    assertThat(findViolations("RawRequestFixture.java", rawRequest))
        .extracting(Violation::category)
        .contains("raw request body");
    assertThat(findViolations("RawBodyFixture.java", rawBody))
        .extracting(Violation::category)
        .contains("raw request body");
    assertThat(findViolations("SafeRequestMetadataFixture.java", safeMetadata)).isEmpty();
  }

  private static Path productionSourceRoot() {
    Path workingDirectory = Path.of("").toAbsolutePath();
    Path direct = workingDirectory.resolve("src/main/java");
    if (Files.isDirectory(direct)) return direct;
    Path nested = workingDirectory.resolve("backend/src/main/java");
    if (Files.isDirectory(nested)) return nested;
    throw new IllegalStateException("Backend production source root not found from " + workingDirectory);
  }

  private static List<Violation> findViolations(String sourceName, String source) throws Exception {
    return findViolations(List.of(new SourceText(sourceName, source)));
  }

  private static List<Violation> findViolations(List<SourceText> sources) throws Exception {
    JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
    if (compiler == null) throw new IllegalStateException("JDK compiler is required");
    DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
    List<JavaFileObject> sourceFiles =
        sources.stream().map(SourceJavaFileObject::new).map(JavaFileObject.class::cast).toList();
    try (StandardJavaFileManager fileManager =
        compiler.getStandardFileManager(diagnostics, Locale.ROOT, StandardCharsets.UTF_8)) {
      JavacTask task =
          (JavacTask)
              compiler.getTask(
                  null, fileManager, diagnostics, List.of("-proc:none"), null, sourceFiles);
      List<CompilationUnitTree> units = new ArrayList<>();
      task.parse().forEach(units::add);
      List<String> parseErrors =
          diagnostics.getDiagnostics().stream()
              .filter(diagnostic -> diagnostic.getKind() == Diagnostic.Kind.ERROR)
              .map(Diagnostic::toString)
              .toList();
      if (!parseErrors.isEmpty()) {
        throw new IllegalStateException("Could not parse Java sources: " + parseErrors);
      }
      Trees trees = Trees.instance(task);
      List<Violation> violations = new ArrayList<>();
      for (CompilationUnitTree unit : units) {
        String sourceName = unit.getSourceFile().getName();
        Set<String> loggerNames = new HashSet<>();
        Set<String> rawRequestObjectNames = new HashSet<>();
        new LoggerDeclarationScanner().scan(unit, loggerNames);
        new RawRequestDeclarationScanner().scan(unit, rawRequestObjectNames);
        new LoggerInvocationScanner(
                sourceName, unit, trees, loggerNames, rawRequestObjectNames, violations)
            .scan(unit, null);
      }
      return violations;
    }
  }

  private static String sensitiveCategory(String name) {
    String normalized = normalizeName(name);
    for (SensitiveName sensitiveName : SENSITIVE_NAMES) {
      if (normalized.contains(sensitiveName.normalizedName())) return sensitiveName.category();
    }
    if (RAW_REQUEST_VALUE_NAMES.contains(normalized) || normalized.endsWith("requestbody")) {
      return "raw request body";
    }
    return null;
  }

  private static String normalizeName(String name) {
    return name.replaceAll("[^A-Za-z0-9]", "").toLowerCase(Locale.ROOT);
  }

  private static final class LoggerDeclarationScanner extends TreeScanner<Void, Set<String>> {
    @Override
    public Void visitClass(ClassTree type, Set<String> loggerNames) {
      boolean usesSlf4j =
          type.getModifiers().getAnnotations().stream()
              .anyMatch(annotation -> annotation.getAnnotationType().toString().endsWith("Slf4j"));
      if (usesSlf4j) loggerNames.add("log");
      return super.visitClass(type, loggerNames);
    }

    @Override
    public Void visitVariable(VariableTree variable, Set<String> loggerNames) {
      if (variable.getType() != null && variable.getType().toString().endsWith("Logger")) {
        loggerNames.add(variable.getName().toString());
      }
      return super.visitVariable(variable, loggerNames);
    }
  }

  private static final class RawRequestDeclarationScanner
      extends TreeScanner<Void, Set<String>> {
    @Override
    public Void visitVariable(VariableTree variable, Set<String> rawRequestObjectNames) {
      if (variable.getType() != null) {
        String normalizedType = normalizeName(variable.getType().toString());
        if (normalizedType.endsWith("httpservletrequest")
            || normalizedType.endsWith("servletrequest")) {
          rawRequestObjectNames.add(variable.getName().toString());
        }
      }
      return super.visitVariable(variable, rawRequestObjectNames);
    }
  }

  private static final class LoggerInvocationScanner extends TreeScanner<Void, Void> {
    private final String sourceName;
    private final CompilationUnitTree unit;
    private final Trees trees;
    private final Set<String> loggerNames;
    private final Set<String> rawRequestObjectNames;
    private final List<Violation> violations;

    private LoggerInvocationScanner(
        String sourceName,
        CompilationUnitTree unit,
        Trees trees,
        Set<String> loggerNames,
        Set<String> rawRequestObjectNames,
        List<Violation> violations) {
      this.sourceName = sourceName;
      this.unit = unit;
      this.trees = trees;
      this.loggerNames = loggerNames;
      this.rawRequestObjectNames = rawRequestObjectNames;
      this.violations = violations;
    }

    @Override
    public Void visitMethodInvocation(MethodInvocationTree invocation, Void unused) {
      if (invocation.getMethodSelect() instanceof MemberSelectTree select
          && LOG_LEVELS.contains(select.getIdentifier().toString())
          && isLoggerReceiver(select.getExpression().toString())) {
        Set<String> categories = new LinkedHashSet<>();
        for (var argument : invocation.getArguments()) {
          if (argument instanceof LiteralTree) continue;
          if (isDirectRawRequestObject(argument)) categories.add("raw request body");
          new SensitiveArgumentScanner(categories).scan(argument, null);
        }
        long start = trees.getSourcePositions().getStartPosition(unit, invocation);
        long line = start < 0 ? -1 : unit.getLineMap().getLineNumber(start);
        for (String category : categories) {
          violations.add(
              new Violation(
                  sourceName,
                  line,
                  select.getIdentifier().toString(),
                  category,
                  invocation.toString()));
        }
      }
      return super.visitMethodInvocation(invocation, unused);
    }

    private boolean isLoggerReceiver(String receiver) {
      return loggerNames.stream()
          .anyMatch(name -> receiver.equals(name) || receiver.endsWith("." + name));
    }

    private boolean isDirectRawRequestObject(com.sun.source.tree.Tree argument) {
      String name = null;
      if (argument instanceof IdentifierTree identifier) {
        name = identifier.getName().toString();
      } else if (argument instanceof MemberSelectTree select) {
        name = select.getIdentifier().toString();
      }
      return name != null
          && (rawRequestObjectNames.contains(name)
              || RAW_REQUEST_OBJECT_NAMES.contains(normalizeName(name)));
    }
  }

  private static final class SensitiveArgumentScanner extends TreeScanner<Void, Void> {
    private final Set<String> categories;

    private SensitiveArgumentScanner(Set<String> categories) {
      this.categories = categories;
    }

    @Override
    public Void visitIdentifier(IdentifierTree identifier, Void unused) {
      add(identifier.getName().toString());
      return super.visitIdentifier(identifier, unused);
    }

    @Override
    public Void visitMemberSelect(MemberSelectTree select, Void unused) {
      add(select.getIdentifier().toString());
      return super.visitMemberSelect(select, unused);
    }

    @Override
    public Void visitLiteral(LiteralTree literal, Void unused) {
      if (literal.getValue() instanceof String value) add(value);
      return super.visitLiteral(literal, unused);
    }

    private void add(String name) {
      String category = sensitiveCategory(name);
      if (category != null) categories.add(category);
    }
  }

  private record SensitiveName(String category, String normalizedName) {}

  private record ContractCase(String category, String expression) {}

  private record SourceText(String name, String source) {}

  private record Violation(String source, long line, String level, String category, String invocation) {}

  private static final class SourceJavaFileObject extends SimpleJavaFileObject {
    private final SourceText sourceText;

    private SourceJavaFileObject(SourceText sourceText) {
      super(
          URI.create(
              "string:///"
                  + sourceText.name().replace('\\', '/').replace(":", "").replace(" ", "%20")),
          JavaFileObject.Kind.SOURCE);
      this.sourceText = sourceText;
    }

    @Override
    public String getName() {
      return sourceText.name();
    }

    @Override
    public CharSequence getCharContent(boolean ignoreEncodingErrors) throws IOException {
      return sourceText.source();
    }
  }
}
