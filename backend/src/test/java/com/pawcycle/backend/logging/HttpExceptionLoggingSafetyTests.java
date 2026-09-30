package com.pawcycle.backend.logging;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.stream.Stream;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import tools.jackson.databind.ObjectMapper;

@ExtendWith(OutputCaptureExtension.class)
class HttpExceptionLoggingSafetyTests {
  static Stream<Arguments> handlers() {
    return Stream.of(
        Arguments.of("commerce.CommerceExceptionHandler", "Unexpected exception while processing commerce request", Exception.class, "INTERNAL_ERROR"),
        Arguments.of("member.api.AuthExceptionHandler", "Unexpected exception while handling authentication request", Exception.class, "INTERNAL_ERROR"),
        Arguments.of("member.address.api.MemberAddressExceptionHandler", "Unexpected exception while processing member address request", Exception.class, "MEMBER_ADDRESS_UNAVAILABLE"),
        Arguments.of("recommendation.RecommendationExceptionHandler", "Unexpected exception while processing recommendation request", Exception.class, "INTERNAL_ERROR"),
        Arguments.of("interaction.InteractionExceptionHandler", "Unexpected exception while recording interaction", Exception.class, "INTERNAL_ERROR"),
        Arguments.of("catalog.admin.api.AdminCatalogExceptionHandler", "Unexpected exception while processing admin catalog request", Exception.class, "ADMIN_CATALOG_UNAVAILABLE"),
        Arguments.of("catalog.engagement.api.ProductEngagementExceptionHandler", "Unexpected exception while processing product engagement request", Exception.class, "INTERNAL_ERROR"),
        Arguments.of("subscription.SubscriptionExceptionHandler", "Unexpected exception while processing subscription request", Exception.class, "INTERNAL_ERROR"),
        Arguments.of("catalog.product.api.ProductExceptionHandler", "Unexpected exception while querying public product list", com.pawcycle.backend.catalog.product.application.ProductListUnavailableException.class, "PRODUCT_LIST_UNAVAILABLE"),
        Arguments.of("catalog.product.api.ProductExceptionHandler", "Unexpected exception while querying public product detail", com.pawcycle.backend.catalog.product.application.ProductDetailUnavailableException.class, "PRODUCT_DETAIL_UNAVAILABLE"));
  }

  @ParameterizedTest
  @MethodSource("handlers")
  void unexpectedHandlersKeepSafeDiagnosticsAndResponses(
      String name, String event, Class<? extends Exception> handledType, String expectedCode, CapturedOutput output)
      throws Exception {
    RuntimeException original = new RuntimeException("TOP_LEVEL_SENTINEL", new IllegalStateException("CAUSE_SENTINEL"));
    original.addSuppressed(new IllegalArgumentException("SUPPRESSED_SENTINEL"));
    Exception failure = handledType == Exception.class ? original
        : handledType.getConstructor(Throwable.class).newInstance(original);
    Class<?> handler = Class.forName("com.pawcycle.backend." + name);
    var constructor = handler.getDeclaredConstructor();
    constructor.setAccessible(true);
    Method method = Arrays.stream(handler.getDeclaredMethods())
        .filter(candidate -> candidate.isAnnotationPresent(ExceptionHandler.class))
        .filter(candidate -> Arrays.asList(candidate.getAnnotation(ExceptionHandler.class).value()).contains(handledType))
        .findFirst().orElseThrow();
    method.setAccessible(true);

    ResponseEntity<?> response = (ResponseEntity<?>) method.invoke(constructor.newInstance(), failure);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
    assertThat(new ObjectMapper().writeValueAsString(response.getBody()))
        .contains("\"code\":\"" + expectedCode + "\"")
        .doesNotContain("TOP_LEVEL_SENTINEL", "CAUSE_SENTINEL", "SUPPRESSED_SENTINEL");
    assertThat(output).contains(event, "exceptionType=" + failure.getClass().getName(),
        "HttpExceptionLoggingSafetyTests.unexpectedHandlersKeepSafeDiagnosticsAndResponses")
        .doesNotContain("TOP_LEVEL_SENTINEL", "CAUSE_SENTINEL", "SUPPRESSED_SENTINEL");
  }
}
