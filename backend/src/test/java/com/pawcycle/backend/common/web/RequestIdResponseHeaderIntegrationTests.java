package com.pawcycle.backend.common.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;

/**
 * Every backend response carries X-Request-ID through the real servlet filter chain, including
 * responses that Spring Security writes itself (401 without a session, 403 without a CSRF token).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class RequestIdResponseHeaderIntegrationTests {
  private final HttpClient client = HttpClient.newHttpClient();
  @LocalServerPort private int port;

  @Test
  void successfulResponseEchoesTheSafeInboundId() throws Exception {
    HttpResponse<Void> response = send(HttpRequest.newBuilder(uri("/api/products"))
        .header("X-Request-ID", "client-req-1").GET());

    assertThat(response.statusCode()).isEqualTo(200);
    assertThat(response.headers().firstValue("X-Request-ID")).hasValue("client-req-1");
  }

  @Test
  void unauthenticatedResponseWrittenBySecurityStillHasAnId() throws Exception {
    HttpResponse<Void> response = send(HttpRequest.newBuilder(uri("/api/auth/me")).GET());

    assertThat(response.statusCode()).isEqualTo(401);
    assertThat(response.headers().firstValue("X-Request-ID").orElseThrow()).matches("[0-9a-f-]{36}");
  }

  @Test
  void rejectedPostWithoutCsrfTokenStillHasAnIdButNotTheUnsafeInboundValue() throws Exception {
    HttpResponse<Void> response = send(HttpRequest.newBuilder(uri("/api/auth/logout"))
        .header("X-Request-ID", "unsafe value")
        .POST(HttpRequest.BodyPublishers.noBody()));

    assertThat(response.statusCode()).isIn(401, 403);
    assertThat(response.headers().firstValue("X-Request-ID").orElseThrow()).matches("[0-9a-f-]{36}");
  }

  private URI uri(String path) {
    return URI.create("http://127.0.0.1:" + port + path);
  }

  private HttpResponse<Void> send(HttpRequest.Builder request) throws Exception {
    return client.send(request.build(), HttpResponse.BodyHandlers.discarding());
  }
}
