package com.pawcycle.backend.interaction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.pawcycle.backend.common.api.CommonValidationExceptionHandler;
import com.pawcycle.backend.member.application.AuthenticatedMemberPrincipal;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class InteractionControllerTests {
  private final InteractionService service = mock(InteractionService.class);
  private MockMvc mvc;

  @BeforeEach
  void setUp() {
    SecurityContextHolder.getContext().setAuthentication(
        UsernamePasswordAuthenticationToken.authenticated(
            new AuthenticatedMemberPrincipal(7L), null, java.util.List.of()));
    mvc = MockMvcBuilders.standaloneSetup(new InteractionController(service))
        .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
        .setControllerAdvice(new CommonValidationExceptionHandler(), new InteractionExceptionHandler())
        .build();
  }

  @AfterEach
  void clearContext() {
    SecurityContextHolder.clearContext();
  }

  @ParameterizedTest
  @ValueSource(strings = {"", "{RAW_BODY_SENTINEL", "{\"events\":\"RAW_BODY_SENTINEL\"}"})
  void unreadableBodyKeepsInteractionErrorShape(String input) throws Exception {
    String response = mvc.perform(post("/api/interactions").contentType(MediaType.APPLICATION_JSON).content(input))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
        .andExpect(jsonPath("$.message").value("요청 값을 확인해 주세요."))
        .andExpect(jsonPath("$.fieldErrors").doesNotExist())
        .andExpect(jsonPath("$.length()").value(2))
        .andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
    assertThat(response).doesNotContain("RAW_BODY_SENTINEL", "JsonParseException", "Cannot deserialize");
    verifyNoInteractions(service);
  }

  @Test
  void parsedBodyStillUsesServiceValidation() throws Exception {
    doThrow(new InteractionException(400, "VALIDATION_FAILED", "existing validation"))
        .when(service).record(eq(7L), any());
    mvc.perform(post("/api/interactions").contentType(MediaType.APPLICATION_JSON).content("{}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.message").value("existing validation"))
        .andExpect(jsonPath("$.fieldErrors").doesNotExist());
    verify(service).record(7L, null);
  }

  @Test
  void parsedBatchKeepsSuccessfulResponse() throws Exception {
    mvc.perform(post("/api/interactions").contentType(MediaType.APPLICATION_JSON).content("{\"events\":[]}"))
        .andExpect(status().isNoContent());
    verify(service).record(7L, java.util.List.of());
  }
}
