package com.pawcycle.backend.commerce.payment.infrastructure.toss;

import static org.assertj.core.api.Assertions.assertThat;

import com.sun.net.httpserver.HttpServer;
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import tools.jackson.databind.ObjectMapper;

class TossPaymentQuerySafetyTests {
  private FakeServer fake;
  private TossPaymentAdapter adapter;

  @BeforeEach void start() throws Exception {
    fake = new FakeServer();
    adapter = fake.adapter();
  }
  @AfterEach void stop() { fake.close(); }

  @ParameterizedTest
  @ValueSource(ints = {400, 401, 403, 404, 500})
  void queryHttpErrorsNeverMeanPaymentFailure(int http) {
    fake.respond(http, "{\"code\":\"LOOKUP_ERROR\"}");
    assertThat(adapter.queryPayment("order-1", "key-1", new BigDecimal("123.45")))
        .isEqualTo(new TossPaymentAdapter.ConfirmResult("UNKNOWN", "QUERY_HTTP_" + http));
    fake.assertQueryOnly();
  }

  @ParameterizedTest
  @CsvSource({"DONE,SUCCEEDED", "ABORTED,FAILED", "EXPIRED,FAILED", "READY,UNKNOWN",
      "IN_PROGRESS,UNKNOWN", "WAITING_FOR_DEPOSIT,UNKNOWN", "CANCELED,UNKNOWN",
      "PARTIAL_CANCELED,UNKNOWN", "FUTURE_STATUS,UNKNOWN"})
  void onlyMatchingIdentityAndExplicitFinalStatusAreConclusive(String providerStatus, String result) {
    fake.respond(200, FakeServer.payment("order-1", "key-1", "123.450", providerStatus));
    assertThat(adapter.queryPayment("order-1", "key-1", new BigDecimal("123.45")))
        .isEqualTo(new TossPaymentAdapter.ConfirmResult(result, providerStatus));
    fake.assertQueryOnly();
  }

  @ParameterizedTest
  @ValueSource(strings = {
      "{}", "null", "[]", "broken-json",
      "{\"orderId\":42,\"paymentKey\":\"key-1\",\"totalAmount\":123.45,\"status\":\"DONE\"}",
      "{\"orderId\":\"order-1\",\"paymentKey\":null,\"totalAmount\":123.45,\"status\":\"DONE\"}",
      "{\"orderId\":\"order-1\",\"paymentKey\":\"key-1\",\"totalAmount\":\"123.45\",\"status\":\"DONE\"}",
      "{\"orderId\":\"order-1\",\"paymentKey\":\"key-1\",\"totalAmount\":123.45,\"status\":null}"
  })
  void missingMalformedOrWrongTypeIdentityIsUnknown(String response) {
    fake.respond(200, response);
    assertThat(adapter.queryPayment("order-1", "key-1", new BigDecimal("123.45")).status())
        .isEqualTo("UNKNOWN");
    fake.assertQueryOnly();
  }

  @ParameterizedTest
  @CsvSource({"other-order,key-1,123.45", "order-1,other-key,123.45", "order-1,key-1,124.45"})
  void mismatchedOrderKeyOrAmountCannotCompletePayment(String order, String key, String amount) {
    fake.respond(200, FakeServer.payment(order, key, amount, "DONE"));
    assertThat(adapter.queryPayment("order-1", "key-1", new BigDecimal("123.45")))
        .isEqualTo(new TossPaymentAdapter.ConfirmResult("UNKNOWN", "IDENTITY_MISMATCH"));
    fake.assertQueryOnly();
  }

  @Test void amountComparisonNeverRoundsDistinctDecimalValuesIntoAMatch() {
    fake.respond(200, FakeServer.payment("order-1", "key-1", "9999999999999997.99", "DONE"));
    assertThat(adapter.queryPayment("order-1", "key-1", new BigDecimal("9999999999999998.00")))
        .isEqualTo(new TossPaymentAdapter.ConfirmResult("UNKNOWN", "IDENTITY_MISMATCH"));
    fake.assertQueryOnly();
  }

  @Test void missingLocalIdentityDoesNotCallProvider() {
    assertThat(adapter.queryPayment("order-1", null, BigDecimal.TEN).status()).isEqualTo("UNKNOWN");
    assertThat(fake.requests.get()).isZero();
  }

  @Test void timeoutIsUnknownWithoutAnyConfirm() {
    fake.releaseResponse = new CountDownLatch(1);
    assertThat(adapter.queryPayment("order-1", "key-1", new BigDecimal("123.45")))
        .isEqualTo(new TossPaymentAdapter.ConfirmResult("UNKNOWN", "QUERY_UNAVAILABLE"));
    fake.assertQueryOnly();
  }

  @Test void confirmKeepsExistingExplicitRejectionContract() {
    fake.respond(400, "{\"code\":\"REJECTED_CARD\"}");
    assertThat(adapter.confirm("key-1", "order-1", new BigDecimal("123.45")))
        .isEqualTo(new TossPaymentAdapter.ConfirmResult("FAILED", "REJECTED_CARD"));
    assertThat(fake.lastMethod).isEqualTo("POST");
    assertThat(fake.lastPath).isEqualTo("/v1/payments/confirm");
  }

  static final class FakeServer implements AutoCloseable {
    final HttpServer server;
    final java.util.concurrent.ExecutorService worker = Executors.newCachedThreadPool();
    final AtomicInteger requests = new AtomicInteger();
    volatile int http = 200;
    volatile String body = payment("order-1", "key-1", "123.45", "DONE");
    volatile String lastMethod;
    volatile String lastPath;
    volatile CountDownLatch releaseResponse;
    FakeServer() throws Exception {
      server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
      server.setExecutor(worker);
      server.createContext("/", exchange -> {
        requests.incrementAndGet();
        lastMethod = exchange.getRequestMethod();
        lastPath = exchange.getRequestURI().getPath();
        try {
          if (releaseResponse != null) releaseResponse.await(15, TimeUnit.SECONDS);
          byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
          exchange.sendResponseHeaders(http, bytes.length);
          exchange.getResponseBody().write(bytes);
        } catch (InterruptedException interrupted) {
          Thread.currentThread().interrupt();
        } finally { exchange.close(); }
      });
      server.start();
    }
    TossPaymentAdapter adapter() {
      return new TossTestPaymentAdapter("test_sk_fixture_only",
          "http://127.0.0.1:" + server.getAddress().getPort(), new ObjectMapper(), HttpClient.newHttpClient());
    }
    void respond(int code, String response) { http = code; body = response; }
    void assertQueryOnly() {
      assertThat(requests.get()).isEqualTo(1);
      assertThat(lastMethod).isEqualTo("GET");
      assertThat(lastPath).isEqualTo("/v1/payments/orders/order-1");
    }
    static String payment(String order, String key, String amount, String status) {
      return "{\"orderId\":\"%s\",\"paymentKey\":\"%s\",\"totalAmount\":%s,\"status\":\"%s\"}"
          .formatted(order, key, amount, status);
    }
    public void close() {
      if (releaseResponse != null) releaseResponse.countDown();
      server.stop(0);
      worker.shutdownNow();
    }
  }
}