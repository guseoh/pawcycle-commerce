package com.pawcycle.backend.common.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.pawcycle.backend.PawcycleBackendApplication;
import com.pawcycle.backend.member.domain.Member;
import com.pawcycle.backend.member.persistence.MemberRepository;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import tools.jackson.databind.ObjectMapper;

/**
 * Drives a real embedded server over HTTP the way the browser does: one JSESSIONID cookie and a
 * CSRF token kept in memory. Used to recreate the application context between requests.
 */
final class SessionStoreTestClient {

  static final String SESSION_COOKIE = "JSESSIONID";
  private static final ObjectMapper JSON = new ObjectMapper();

  private final HttpClient http = HttpClient.newHttpClient();
  private int port;
  private String sessionCookie;
  private String csrfToken;
  private final List<String> sessionSetCookies = new ArrayList<>();

  static ConfigurableApplicationContext start(String store, String... extraProperties) {
    // Command-line arguments outrank application.properties (default properties would not).
    List<String> arguments =
        new ArrayList<>(List.of("--server.port=0", "--pawcycle.session.store=" + store));
    for (String property : extraProperties) {
      arguments.add("--" + property);
    }
    return new SpringApplicationBuilder(PawcycleBackendApplication.class)
        .profiles("test")
        .run(arguments.toArray(String[]::new));
  }

  static TestMember createMember(ConfigurableApplicationContext context) {
    String password = "session-store-" + UUID.randomUUID();
    Member member =
        context
            .getBean(MemberRepository.class)
            .saveAndFlush(
                new Member(
                    "session-store-" + UUID.randomUUID() + "@example.com",
                    context.getBean(PasswordEncoder.class).encode(password)));
    return new TestMember(member.getId(), member.getEmail(), password);
  }

  static void deleteMember(ConfigurableApplicationContext context, long memberId) {
    JdbcTemplate jdbc = context.getBean(JdbcTemplate.class);
    jdbc.update("UPDATE members SET default_address_id = NULL WHERE id = ?", memberId);
    jdbc.update("DELETE FROM member_addresses WHERE member_id = ?", memberId);
    jdbc.update("DELETE FROM members WHERE id = ?", memberId);
  }

  SessionStoreTestClient use(ConfigurableApplicationContext context) {
    this.port = Integer.parseInt(context.getEnvironment().getRequiredProperty("local.server.port"));
    return this;
  }

  SessionStoreTestClient withSessionCookie(String value) {
    this.sessionCookie = value;
    return this;
  }

  String sessionCookie() {
    return sessionCookie;
  }

  String csrfToken() {
    return csrfToken;
  }

  List<String> sessionSetCookies() {
    return sessionSetCookies;
  }

  HttpResponse<String> fetchCsrfToken() {
    HttpResponse<String> response = send(request("/api/auth/csrf").GET());
    assertThat(response.statusCode()).isEqualTo(200);
    csrfToken = JSON.readTree(response.body()).get("token").asString();
    return response;
  }

  HttpResponse<String> login(TestMember member) {
    String body =
        JSON.writeValueAsString(
            java.util.Map.of("email", member.email(), "password", member.password()));
    return send(json(request("/api/auth/login"), body).header("X-CSRF-TOKEN", csrfToken));
  }

  HttpResponse<String> get(String path) {
    return send(request(path).GET());
  }

  HttpResponse<String> me() {
    return send(request("/api/auth/me").GET());
  }

  HttpResponse<String> createAddress(boolean withCsrfToken) {
    String body =
        "{\"name\":\"집\",\"recipientName\":\"보호자\",\"recipientPhone\":\"010-0000-0000\","
            + "\"postalCode\":\"06236\",\"addressLine1\":\"서울\",\"addressLine2\":null}";
    HttpRequest.Builder builder = json(request("/api/addresses"), body);
    if (withCsrfToken) {
      builder.header("X-CSRF-TOKEN", csrfToken);
    }
    return send(builder);
  }

  HttpResponse<String> logout() {
    return send(
        request("/api/auth/logout")
            .POST(HttpRequest.BodyPublishers.noBody())
            .header("X-CSRF-TOKEN", csrfToken));
  }

  private HttpRequest.Builder request(String path) {
    HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path));
    if (sessionCookie != null) {
      builder.header("Cookie", SESSION_COOKIE + "=" + sessionCookie);
    }
    return builder;
  }

  private static HttpRequest.Builder json(HttpRequest.Builder builder, String body) {
    return builder
        .header("Content-Type", "application/json")
        .POST(HttpRequest.BodyPublishers.ofString(body));
  }

  private HttpResponse<String> send(HttpRequest.Builder builder) {
    try {
      HttpResponse<String> response = http.send(builder.build(), HttpResponse.BodyHandlers.ofString());
      for (String setCookie : response.headers().allValues("Set-Cookie")) {
        if (!setCookie.startsWith(SESSION_COOKIE + "=")) {
          continue;
        }
        sessionSetCookies.add(setCookie);
        String value = setCookie.substring(SESSION_COOKIE.length() + 1, setCookie.indexOf(';'));
        sessionCookie = value.isEmpty() ? null : value;
      }
      return response;
    } catch (IOException exception) {
      throw new IllegalStateException(exception);
    } catch (InterruptedException exception) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException(exception);
    }
  }

  /**
   * AUTH-003 DR2: a cookie that carries a session id keeps the approved attributes. Logout clearing
   * cookies (empty value, expired) only need the same path; the existing CookieClearingLogoutHandler
   * writes them without HttpOnly in both stores.
   */
  static void assertSessionCookieContract(SessionStoreTestClient client) {
    assertThat(client.sessionSetCookies()).isNotEmpty();
    for (String setCookie : client.sessionSetCookies()) {
      System.out.println("AUTH-005 Set-Cookie: " + setCookie.replaceFirst("=[^;]+", "=<redacted>"));
      String lower = setCookie.toLowerCase();
      assertThat(lower).contains("; path=/").doesNotContain("domain=");
      if (setCookie.startsWith(SESSION_COOKIE + "=;")) {
        assertThat(lower).containsAnyOf("max-age=0", "expires=thu, 01 jan 1970");
        continue;
      }
      assertThat(lower).contains("; httponly").contains("samesite=lax");
      // test profile sets server.servlet.session.cookie.secure=false (local HTTP)
      assertThat(lower).doesNotContain("; secure");
      assertThat(lower).doesNotContain("max-age").doesNotContain("expires");
    }
  }

  record TestMember(long id, String email, String password) {}
}
