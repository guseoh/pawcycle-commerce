package com.pawcycle.backend.recommendation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.pawcycle.backend.common.api.CommonValidationExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class RecommendationValidationTests {
  private final RecommendationService service = mock(RecommendationService.class);
  private MockMvc mvc;

  @BeforeEach
  void setUp() {
    mvc = MockMvcBuilders.standaloneSetup(new RecommendationController(service))
        .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
        .setControllerAdvice(new CommonValidationExceptionHandler(), new RecommendationExceptionHandler())
        .build();
  }

  @Test
  void missingPetIdUsesCommonValidationContract() throws Exception {
    mvc.perform(get("/api/recommendations/products"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
        .andExpect(jsonPath("$.message").value("요청 값을 확인해 주세요."))
        .andExpect(jsonPath("$.fieldErrors.length()").value(1))
        .andExpect(jsonPath("$.fieldErrors[0].field").value("petId"));
    verifyNoInteractions(service);
  }

  @ParameterizedTest
  @ValueSource(strings = {"RAW_PET_ID_SENTINEL", "9223372036854775808", "-9223372036854775809"})
  void invalidPetIdIsNotExposed(String input) throws Exception {
    String response = mvc.perform(get("/api/recommendations/products").param("petId", input))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
        .andExpect(jsonPath("$.fieldErrors[0].field").value("petId"))
        .andExpect(jsonPath("$.fieldErrors[0].message").value("요청 값의 형식이 올바르지 않습니다."))
        .andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
    assertThat(response).doesNotContain(input, "NumberFormatException", "Failed to convert");
    verifyNoInteractions(service);
  }
}
