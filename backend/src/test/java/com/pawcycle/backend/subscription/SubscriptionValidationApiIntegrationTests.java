package com.pawcycle.backend.subscription;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.pawcycle.backend.member.application.AuthenticatedMemberPrincipal;
import com.pawcycle.backend.member.domain.MemberRole;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

@SpringBootTest
@ActiveProfiles("test")
class SubscriptionValidationApiIntegrationTests {
  private final WebApplicationContext applicationContext;
  private MockMvc mockMvc;

  @Autowired
  SubscriptionValidationApiIntegrationTests(WebApplicationContext applicationContext) {
    this.applicationContext = applicationContext;
  }

  @BeforeEach
  void setUp() {
    mockMvc =
        MockMvcBuilders.webAppContextSetup(applicationContext).apply(springSecurity()).build();
  }

  @Test
  void invalidSubscriptionRequestIsHandledAsValidationInsteadOfInternalError() throws Exception {
    mockMvc
        .perform(
            post("/api/subscriptions")
                .with(user())
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"petId\":null,\"planVersionId\":0,\"deliveryCycleWeeks\":9}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
        .andExpect(jsonPath("$.fieldErrors.length()").value(4))
        .andExpect(jsonPath("$.fieldErrors[0].field").value("deliveryCycleWeeks"))
        .andExpect(jsonPath("$.fieldErrors[1].field").value("deliveryCycleWeeks"))
        .andExpect(jsonPath("$.fieldErrors[2].field").value("petId"))
        .andExpect(jsonPath("$.fieldErrors[3].field").value("planVersionId"));
  }

  private org.springframework.test.web.servlet.request.RequestPostProcessor user() {
    return authentication(
        new UsernamePasswordAuthenticationToken(
            new AuthenticatedMemberPrincipal(7L, MemberRole.USER),
            null,
            List.of(new SimpleGrantedAuthority("ROLE_USER"))));
  }
}
