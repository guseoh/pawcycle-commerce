package com.pawcycle.backend.common.external;

import static org.assertj.core.api.Assertions.assertThat;

import com.openai.core.http.Headers;
import com.openai.errors.InternalServerException;
import com.openai.errors.OpenAIInvalidDataException;
import com.openai.errors.OpenAIIoException;
import com.openai.errors.RateLimitException;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.net.http.HttpConnectTimeoutException;
import java.net.http.HttpTimeoutException;
import java.util.concurrent.CompletionException;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class ExternalFailureClassifierTests {

  @Test
  void timeoutsAreTimeouts() {
    assertThat(classify(new HttpTimeoutException("request timed out"))).isEqualTo(FailureClass.TIMEOUT);
    assertThat(classify(new SocketTimeoutException("read timed out"))).isEqualTo(FailureClass.TIMEOUT);
    assertThat(classify(new InterruptedException())).isEqualTo(FailureClass.TIMEOUT);
  }

  @Test
  void unreachableProviderIsConnectEvenWhenTheConnectStepTimedOut() {
    assertThat(classify(new HttpConnectTimeoutException("connect timed out")))
        .isEqualTo(FailureClass.CONNECT);
    assertThat(classify(new ConnectException("refused"))).isEqualTo(FailureClass.CONNECT);
    assertThat(classify(new UnknownHostException("api.example"))).isEqualTo(FailureClass.CONNECT);
    assertThat(classify(new IOException("connection reset"))).isEqualTo(FailureClass.CONNECT);
  }

  @Test
  void unreadableResponsesAreParseFailures() {
    Exception jackson = readBrokenJson();
    assertThat(classify(jackson)).isEqualTo(FailureClass.PARSE);
    assertThat(classify(new OpenAIInvalidDataException("bad body"))).isEqualTo(FailureClass.PARSE);
  }

  @Test
  void openAiStatusDecidesBetweenServerErrorAndRejection() {
    Headers headers = Headers.builder().build();
    assertThat(classify(InternalServerException.builder().statusCode(503).headers(headers).build()))
        .isEqualTo(FailureClass.PROVIDER_5XX);
    assertThat(classify(RateLimitException.builder().headers(headers).build()))
        .isEqualTo(FailureClass.PROVIDER_REJECTED);
    assertThat(classify(new OpenAIIoException("io", new SocketTimeoutException("read"))))
        .isEqualTo(FailureClass.TIMEOUT);
  }

  @Test
  void wrappedCausesAreFound() {
    assertThat(classify(new CompletionException(new UncheckedIOException(new ConnectException("x")))))
        .isEqualTo(FailureClass.CONNECT);
    assertThat(classify(new IllegalStateException("wrapper", new HttpTimeoutException("t"))))
        .isEqualTo(FailureClass.TIMEOUT);
  }

  @Test
  void adapterClassificationWins() {
    assertThat(classify(new ExternalCallException(FailureClass.PROVIDER_5XX, "HTTP 502")))
        .isEqualTo(FailureClass.PROVIDER_5XX);
    assertThat(ExternalCallException.of(new ConnectException("x")).failureClass())
        .isEqualTo(FailureClass.CONNECT);
  }

  @Test
  void ourOwnDefectsAreInternalNotProviderOutages() {
    assertThat(classify(new NullPointerException())).isEqualTo(FailureClass.INTERNAL);
    assertThat(classify(new IllegalStateException("bug"))).isEqualTo(FailureClass.INTERNAL);
    assertThat(classify(new IllegalArgumentException("bug", new ArithmeticException())))
        .isEqualTo(FailureClass.INTERNAL);
  }

  @Test
  void selfReferencingCauseChainTerminates() {
    RuntimeException outer = new RuntimeException("outer");
    RuntimeException inner = new RuntimeException("inner", outer);
    outer.initCause(inner);
    assertThat(classify(outer)).isEqualTo(FailureClass.INTERNAL);
  }

  private static FailureClass classify(Throwable failure) {
    return ExternalFailureClassifier.classify(failure);
  }

  private static Exception readBrokenJson() {
    try {
      new ObjectMapper().readTree("broken-json");
      throw new AssertionError("expected a parse failure");
    } catch (RuntimeException expected) {
      return expected;
    }
  }
}
