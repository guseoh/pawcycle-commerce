package com.pawcycle.backend.common.error;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.pawcycle.backend.common.web.RequestCorrelationFilter;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.HttpMethod;
import org.springframework.web.HttpRequestMethodNotSupportedException;

@ExtendWith(OutputCaptureExtension.class)
class UnexpectedErrorsTests {
  private final org.slf4j.Logger log = LoggerFactory.getLogger(UnexpectedErrorsTests.class);

  @AfterEach
  void clearMdc() {
    MDC.clear();
  }

  @Test
  void unexpectedExceptionBecomesInternalErrorWithRequestIdAndRedactedLog(CapturedOutput output)
      throws Exception {
    MDC.put(RequestCorrelationFilter.REQUEST_ID_MDC_KEY, "req-500");

    var response = UnexpectedErrors.internalError(
        log, "product", new IllegalStateException("value owner@example.com"));

    assertThat(response.getStatusCode().value()).isEqualTo(500);
    assertThat(response.getBody()).isEqualTo(
        new ApiErrorResponse("INTERNAL_ERROR", "요청을 처리할 수 없습니다.", java.util.List.of(), "req-500"));
    assertThat(output.getOut())
        .contains("area=product, cause=IllegalStateException")
        .contains("\tat ")
        .doesNotContain("owner@example.com");
  }

  @Test
  void springRequestErrorsKeepTheirOwnStatus() {
    var methodNotAllowed = new HttpRequestMethodNotSupportedException("DELETE", Set.of(HttpMethod.GET.name()));

    assertThatThrownBy(() -> UnexpectedErrors.internalError(log, "product", methodNotAllowed))
        .isSameAs(methodNotAllowed);
  }

  @Test
  void errorBodyOmitsRequestIdOutsideARequest() {
    assertThat(ApiErrorResponse.withoutFieldErrors("X", "y").requestId()).isNull();
  }
}
