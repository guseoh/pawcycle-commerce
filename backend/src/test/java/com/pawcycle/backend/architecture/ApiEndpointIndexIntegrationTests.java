package com.pawcycle.backend.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

/**
 * Keeps docs/api/current-endpoints.md equal to the HTTP mappings Spring actually registers.
 *
 * <p>When a controller mapping changes, regenerate the index with
 * {@code ./gradlew test --tests '*ApiEndpointIndexIntegrationTests' -Dpawcycle.api-index.update=true}
 * and commit the updated document together with the code change.
 */
@SpringBootTest
@ActiveProfiles("test")
class ApiEndpointIndexIntegrationTests {
  private static final Path INDEX = Path.of("..", "docs", "api", "current-endpoints.md");
  private static final String BEGIN = "<!-- api-endpoint-index:begin -->";
  private static final String END = "<!-- api-endpoint-index:end -->";

  @Autowired
  @Qualifier("requestMappingHandlerMapping")
  private RequestMappingHandlerMapping handlerMapping;

  @Test
  void endpointIndexMatchesRegisteredMappings() throws IOException {
    String generated = render(endpoints());
    String document = Files.readString(INDEX, StandardCharsets.UTF_8).replace("\r\n", "\n");
    int begin = document.indexOf(BEGIN);
    int end = document.indexOf(END);
    assertThat(begin).as("index start marker").isNotNegative();
    assertThat(end).as("index end marker").isGreaterThan(begin);

    if (Boolean.getBoolean("pawcycle.api-index.update")) {
      String updated =
          document.substring(0, begin + BEGIN.length()) + "\n" + generated + document.substring(end);
      Files.writeString(INDEX, updated, StandardCharsets.UTF_8);
      return;
    }
    String current = document.substring(begin + BEGIN.length(), end).strip();
    assertThat(current)
        .as(
            "docs/api/current-endpoints.md is out of date. Regenerate it with "
                + "-Dpawcycle.api-index.update=true and commit the result.")
        .isEqualTo(generated.strip());
  }

  private List<Endpoint> endpoints() {
    List<Endpoint> endpoints = new ArrayList<>();
    for (Map.Entry<RequestMappingInfo, HandlerMethod> entry :
        handlerMapping.getHandlerMethods().entrySet()) {
      HandlerMethod handler = entry.getValue();
      if (!handler.getBeanType().getName().startsWith("com.pawcycle.backend.")) continue;
      RequestMappingInfo info = entry.getKey();
      List<String> methods =
          info.getMethodsCondition().getMethods().isEmpty()
              ? List.of("ANY")
              : info.getMethodsCondition().getMethods().stream().map(Enum::name).sorted().toList();
      for (String path : info.getPatternValues()) {
        for (String method : methods) {
          endpoints.add(
              new Endpoint(
                  path,
                  method,
                  handler.getBeanType().getSimpleName() + "." + handler.getMethod().getName()));
        }
      }
    }
    endpoints.sort(
        Comparator.comparing(Endpoint::path)
            .thenComparing(Endpoint::method)
            .thenComparing(Endpoint::handler));
    return endpoints;
  }

  private static String render(List<Endpoint> endpoints) {
    StringBuilder out = new StringBuilder();
    out.append("총 ").append(endpoints.size()).append("개\n\n");
    out.append("| HTTP | 경로 | 영역 | 처리 메서드 |\n| --- | --- | --- | --- |\n");
    for (Endpoint endpoint : endpoints) {
      out.append("| ")
          .append(endpoint.method())
          .append(" | `")
          .append(endpoint.path())
          .append("` | ")
          .append(area(endpoint.path()))
          .append(" | `")
          .append(endpoint.handler())
          .append("` |\n");
    }
    return out.toString();
  }

  private static String area(String path) {
    if (path.startsWith("/api/admin")) return "관리자";
    if (path.startsWith("/api/auth")) return "인증";
    if (path.startsWith("/internal")) return "내부";
    return "고객";
  }

  private record Endpoint(String path, String method, String handler) {}
}
