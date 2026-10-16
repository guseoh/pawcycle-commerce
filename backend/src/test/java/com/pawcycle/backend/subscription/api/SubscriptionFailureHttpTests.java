package com.pawcycle.backend.subscription.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.pawcycle.backend.member.application.AuthenticatedMemberPrincipal;
import com.pawcycle.backend.subscription.application.RepeatPurchaseApplicationService;
import com.pawcycle.backend.subscription.application.SubscriptionConversionApplicationService;
import com.pawcycle.backend.subscription.application.SubscriptionCycleSuggestionApplicationService;
import com.pawcycle.backend.subscription.application.SubscriptionService;
import com.pawcycle.backend.subscription.domain.SubscriptionFailure;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class SubscriptionFailureHttpTests {
  private SubscriptionService subscriptions;
  private SubscriptionCycleSuggestionApplicationService suggestions;
  private MockMvc http;

  @BeforeEach
  void setup() {
    subscriptions = mock(SubscriptionService.class);
    suggestions = mock(SubscriptionCycleSuggestionApplicationService.class);
    SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
        new AuthenticatedMemberPrincipal(7L), null, List.of()));
    http = MockMvcBuilders.standaloneSetup(new SubscriptionController(subscriptions),
        new RepeatCommerceController(mock(RepeatPurchaseApplicationService.class), suggestions,
            mock(SubscriptionConversionApplicationService.class)))
        .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
        .setControllerAdvice(new SubscriptionExceptionHandler()).build();
  }

  @AfterEach
  void clearSecurityContext() { SecurityContextHolder.clearContext(); }

  @ParameterizedTest
  @MethodSource("failures")
  void bothAdviceTargetsMapEveryKindToTheExactLegacyHttpResponse(SubscriptionFailure.Kind kind,
      int expectedStatus, String code, String message) throws Exception {
    var cause = new NumberFormatException("original cause");
    for (String url : List.of("/api/subscriptions/11", "/api/subscriptions/11/cycle-suggestion")) {
      failWith(new SubscriptionApiException(expectedStatus, code, message, cause));
      var before = http.perform(get(url)).andExpect(status().is(expectedStatus)).andReturn().getResponse();
      failWith(new SubscriptionFailure(kind, code, message, cause));
      http.perform(get(url)).andExpect(status().is(expectedStatus))
          .andExpect(content().string(before.getContentAsString()))
          .andExpect(content().json("{\"code\":\"" + code + "\",\"message\":\"" + message + "\",\"fieldErrors\":[]}"));
    }
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {"invalid", "\"999999999999999999999999\""})
  void controllerSyntaxStillUsesTheApiExceptionBeforeCallingTheApplication(String header) throws Exception {
    var request = post("/api/subscriptions/11/commands/pause").header("Idempotency-Key", "replay-key");
    if (header != null) request.header("If-Match", header);
    var result = http.perform(request).andExpect(status().is(header == null ? 428 : 400)).andReturn();
    assertThat(result.getResolvedException()).isInstanceOfSatisfying(SubscriptionApiException.class, error -> {
      assertThat(error.code()).isEqualTo(header == null ? "IF_MATCH_REQUIRED" : "IF_MATCH_INVALID");
      assertThat(error.getMessage()).isEqualTo(header == null ? "If-Match가 필요합니다." : "If-Match 형식이 올바르지 않습니다.");
      if (header != null && header.startsWith("\"")) assertThat(error).hasCauseInstanceOf(NumberFormatException.class);
    });
    verifyNoInteractions(subscriptions);
  }

  @Test
  void unexpectedHandlerRemainsTheExistingInternalErrorBoundary() throws Exception {
    failWith(new IllegalStateException("test unexpected failure"));
    http.perform(get("/api/subscriptions/11")).andExpect(status().isInternalServerError())
        .andExpect(content().json("{\"code\":\"INTERNAL_ERROR\",\"message\":\"요청을 처리할 수 없습니다.\",\"fieldErrors\":[]}"));
  }

  private void failWith(RuntimeException error) {
    doThrow(error).when(subscriptions).subscription(anyLong(), anyLong(), anyInt(), anyInt(), anyInt(), anyInt());
    doThrow(error).when(suggestions).cycleSuggestion(anyLong(), anyLong());
  }

  static Stream<Arguments> failures() {
    return Stream.of(
        Arguments.of(SubscriptionFailure.Kind.BAD_REQUEST, 400, "IF_MATCH_INVALID", "If-Match 형식이 올바르지 않습니다."),
        Arguments.of(SubscriptionFailure.Kind.NOT_FOUND, 404, "PET_NOT_FOUND", "Pet을 찾을 수 없습니다."),
        Arguments.of(SubscriptionFailure.Kind.CONFLICT, 409, "IDEMPOTENCY_KEY_REUSED", "동일 key에 다른 요청 본문을 사용할 수 없습니다."),
        Arguments.of(SubscriptionFailure.Kind.PRECONDITION_FAILED, 412, "SUBSCRIPTION_VERSION_MISMATCH", "Subscription version이 일치하지 않습니다."),
        Arguments.of(SubscriptionFailure.Kind.PRECONDITION_REQUIRED, 428, "IF_MATCH_REQUIRED", "If-Match가 필요합니다."));
  }
}
