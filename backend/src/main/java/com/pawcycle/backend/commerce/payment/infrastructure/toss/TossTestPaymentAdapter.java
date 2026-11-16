package com.pawcycle.backend.commerce.payment.infrastructure.toss;

import com.pawcycle.backend.commerce.payment.application.TossPaymentAdapter;

import com.pawcycle.backend.common.external.ExternalCall;
import com.pawcycle.backend.common.external.ExternalCallException;
import com.pawcycle.backend.common.external.ExternalCallFailures;
import com.pawcycle.backend.common.external.FailureClass;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.DeserializationFeature;

/** Explicitly opt-in Toss Test adapter. It is never available outside local-integration. */
@Component
@Profile("local-integration")
@ConditionalOnProperty(name = "pawcycle.toss.test.enabled", havingValue = "true")
class TossTestPaymentAdapter implements TossPaymentAdapter {
  private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(10);
  private static final String TOSS_API_BASE_URL = "https://api.tosspayments.com";

  private final String secretKey;
  private final String baseUrl;
  private final ObjectMapper objectMapper;
  private final HttpClient httpClient;
  private final ExternalCallFailures failures;

  @Autowired
  TossTestPaymentAdapter(
      @Value("${pawcycle.toss.test.secret-key:}") String secretKey,
      ObjectMapper objectMapper,
      ExternalCallFailures failures) {
    this(
        secretKey,
        TOSS_API_BASE_URL,
        objectMapper,
        HttpClient.newBuilder().connectTimeout(REQUEST_TIMEOUT).build(),
        failures);
  }

  TossTestPaymentAdapter(
      String secretKey,
      String baseUrl,
      ObjectMapper objectMapper,
      HttpClient httpClient,
      ExternalCallFailures failures) {
    // The browser uses the Toss payment widget, whose payments must be confirmed with the paired
    // widget secret key (test_gsk_). API individual-integration keys (test_sk_) cannot confirm them.
    if (secretKey == null || secretKey.isBlank() || !secretKey.startsWith("test_gsk_")) {
      throw new IllegalStateException(
          "Toss Test secret key is missing or is not a payment-widget test key (test_gsk_).");
    }
    if (baseUrl == null || baseUrl.isBlank()) {
      throw new IllegalStateException("Toss Test API base URL is missing.");
    }
    this.secretKey = secretKey;
    this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
    this.objectMapper = objectMapper;
    this.httpClient = httpClient;
    this.failures = failures;
  }

  @Override
  public boolean browserTestEnabled() {
    return true;
  }

  @Override
  public ConfirmResult confirm(String paymentKey, String providerOrderId, BigDecimal amount) {
    return request(
        "/v1/payments/confirm",
        Map.of(
            "paymentKey", paymentKey,
            "orderId", providerOrderId,
            "amount", amount));
  }

  @Override
  public ConfirmResult queryPayment(String providerOrderId, String paymentKey, BigDecimal amount) {
    if (providerOrderId == null || providerOrderId.isBlank()
        || paymentKey == null || paymentKey.isBlank() || amount == null)
      return new ConfirmResult("UNKNOWN", "IDENTITY_MISSING");
    try {
      HttpRequest query = HttpRequest.newBuilder()
          .uri(URI.create(baseUrl + "/v1/payments/orders/" + encodePath(providerOrderId)))
          .timeout(REQUEST_TIMEOUT).header("Authorization", "Basic " + basicAuth())
          .header("Accept", "application/json").GET().build();
      HttpResponse<String> response = httpClient.send(query, HttpResponse.BodyHandlers.ofString());
      if (response.statusCode() < 200 || response.statusCode() >= 300) {
        failures.record(
            ExternalCall.TOSS_PAYMENT_QUERY,
            response.statusCode() >= 500 ? FailureClass.PROVIDER_5XX : FailureClass.PROVIDER_REJECTED,
            querySubject(providerOrderId),
            "HTTP " + response.statusCode());
        return new ConfirmResult("UNKNOWN", "QUERY_HTTP_" + response.statusCode());
      }
      JsonNode body = objectMapper.reader().with(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS)
          .readTree(response.body());
      if (!body.path("orderId").isTextual() || !body.path("paymentKey").isTextual()
          || !body.path("totalAmount").isNumber() || !body.path("status").isTextual()
          || !providerOrderId.equals(body.path("orderId").asText())
          || !paymentKey.equals(body.path("paymentKey").asText())
          || amount.compareTo(body.path("totalAmount").decimalValue()) != 0)
        return new ConfirmResult("UNKNOWN", "IDENTITY_MISMATCH");
      String providerStatus = body.path("status").asText();
      return new ConfirmResult(switch (providerStatus) {
        case "DONE" -> "SUCCEEDED";
        case "ABORTED", "EXPIRED" -> "FAILED";
        default -> "UNKNOWN";
      }, providerStatus);
    } catch (InterruptedException exception) {
      Thread.currentThread().interrupt();
      failures.record(ExternalCall.TOSS_PAYMENT_QUERY, exception, querySubject(providerOrderId));
      return new ConfirmResult("UNKNOWN", "QUERY_UNAVAILABLE");
    } catch (IOException | RuntimeException exception) {
      failures.record(ExternalCall.TOSS_PAYMENT_QUERY, exception, querySubject(providerOrderId));
      return new ConfirmResult("UNKNOWN", "QUERY_UNAVAILABLE");
    }
  }

  private static String querySubject(String providerOrderId) {
    return "providerOrderId=" + providerOrderId;
  }

  private ConfirmResult request(String path, Map<String, Object> body) {
    try {
      HttpRequest.Builder builder =
          HttpRequest.newBuilder()
              .uri(URI.create(baseUrl + path))
              .timeout(REQUEST_TIMEOUT)
              .header("Authorization", "Basic " + basicAuth())
              .header("Accept", "application/json");
      if (body == null) {
        builder.GET();
      } else {
        builder
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body)));
      }
      HttpResponse<String> response =
          httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
      if (response.statusCode() >= 400 && response.statusCode() < 500) {
        return new ConfirmResult("FAILED", providerCode(response.body()));
      }
      if (response.statusCode() < 200 || response.statusCode() >= 300) {
        // Not a 4xx rejection: the provider may still have approved, so the caller keeps UNKNOWN.
        throw new ExternalCallException(
            FailureClass.PROVIDER_5XX, "Toss HTTP " + response.statusCode());
      }
      return mapProviderResult(response.body());
    } catch (InterruptedException exception) {
      Thread.currentThread().interrupt();
      throw ExternalCallException.of(exception);
    } catch (IOException | RuntimeException exception) {
      // Classified here, while the original cause is still known; the caller records it once.
      throw ExternalCallException.of(exception);
    }
  }

  private ConfirmResult mapProviderResult(String responseBody) throws IOException {
    Map<?, ?> response = objectMapper.readValue(responseBody, Map.class);
    String status = stringValue(response.get("status"));
    if ("DONE".equals(status)) return new ConfirmResult("SUCCEEDED", status);
    if ("CANCELED".equals(status) || "ABORTED".equals(status))
      return new ConfirmResult("FAILED", status);
    return new ConfirmResult("UNKNOWN", status == null ? "UNKNOWN" : status);
  }

  private String providerCode(String responseBody) {
    try {
      Map<?, ?> response = objectMapper.readValue(responseBody, Map.class);
      String code = stringValue(response.get("code"));
      return code == null || code.isBlank() ? "TOSS_REJECTED" : code;
    } catch (RuntimeException ignored) {
      return "TOSS_REJECTED";
    }
  }

  private String basicAuth() {
    return Base64.getEncoder().encodeToString((secretKey + ":").getBytes(StandardCharsets.UTF_8));
  }

  private static String encodePath(String value) {
    return java.net.URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
  }

  private static String stringValue(Object value) {
    return value == null ? null : String.valueOf(value);
  }
}
