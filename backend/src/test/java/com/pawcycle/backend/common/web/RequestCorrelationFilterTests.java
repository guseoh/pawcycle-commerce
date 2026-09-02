package com.pawcycle.backend.common.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class RequestCorrelationFilterTests {
  private final RequestCorrelationFilter filter = new RequestCorrelationFilter();

  @AfterEach
  void clearMdc() {
    MDC.clear();
  }

  @Test
  void usesSafeInboundRequestIdAndClearsItAfterTheRequest() throws Exception {
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.addHeader(RequestCorrelationFilter.REQUEST_ID_HEADER, "checkout-42");
    AtomicReference<String> observed = new AtomicReference<>();

    filter.doFilter(
        request,
        new MockHttpServletResponse(),
        (ignoredRequest, ignoredResponse) ->
            observed.set(MDC.get(RequestCorrelationFilter.REQUEST_ID_MDC_KEY)));

    assertThat(observed).hasValue("checkout-42");
    assertThat(MDC.get(RequestCorrelationFilter.REQUEST_ID_MDC_KEY)).isNull();
  }

  @Test
  void replacesUnsafeInboundRequestIdWithoutReflectingIt() throws Exception {
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.addHeader(RequestCorrelationFilter.REQUEST_ID_HEADER, "token=secret value");
    AtomicReference<String> observed = new AtomicReference<>();
    MockHttpServletResponse response = new MockHttpServletResponse();

    filter.doFilter(
        request,
        response,
        (ignoredRequest, ignoredResponse) ->
            observed.set(MDC.get(RequestCorrelationFilter.REQUEST_ID_MDC_KEY)));

    assertThat(observed.get()).matches("[0-9a-f-]{36}");
    assertThat(response.getHeader(RequestCorrelationFilter.REQUEST_ID_HEADER)).isNull();
  }
}
