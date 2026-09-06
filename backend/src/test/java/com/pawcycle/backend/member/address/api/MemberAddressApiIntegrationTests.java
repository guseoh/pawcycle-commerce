package com.pawcycle.backend.member.address.api;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.pawcycle.backend.member.application.AuthenticatedMemberPrincipal;
import com.pawcycle.backend.member.domain.Member;
import com.pawcycle.backend.member.domain.MemberRole;
import com.pawcycle.backend.member.persistence.MemberRepository;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class MemberAddressApiIntegrationTests {
  private final WebApplicationContext applicationContext;
  private final MemberRepository members;
  private MockMvc mockMvc;
  private Member member;

  @Autowired
  MemberAddressApiIntegrationTests(
      WebApplicationContext applicationContext, MemberRepository members) {
    this.applicationContext = applicationContext;
    this.members = members;
  }

  @BeforeEach
  void setUp() {
    mockMvc =
        MockMvcBuilders.webAppContextSetup(applicationContext).apply(springSecurity()).build();
    member =
        members.saveAndFlush(
            new Member("address-contract-" + UUID.randomUUID() + "@example.test", "fixture"));
  }

  @Test
  void memberAddressBlankNamePreservesFieldError() throws Exception {
    mockMvc
        .perform(
            post("/api/addresses")
                .with(user())
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"name\":\" \",\"recipientName\":\"보호자\",\"recipientPhone\":\"010-0000-0000\",\"postalCode\":\"06236\",\"addressLine1\":\"서울\",\"addressLine2\":null}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
        .andExpect(jsonPath("$.fieldErrors[0].field").value("name"));
  }

  @Test
  void memberAddressLongPhonePreservesFieldError() throws Exception {
    mockMvc
        .perform(
            post("/api/addresses")
                .with(user())
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"name\":\"집\",\"recipientName\":\"보호자\",\"recipientPhone\":\""
                        + "x".repeat(31)
                        + "\",\"postalCode\":\"06236\",\"addressLine1\":\"서울\",\"addressLine2\":null}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
        .andExpect(jsonPath("$.fieldErrors[*].field").value(org.hamcrest.Matchers.hasItem("recipientPhone")));
  }

  @Test
  void unknownAddressKeepsFeatureNotFoundContract() throws Exception {
    mockMvc
        .perform(
            patch("/api/addresses/999999")
                .with(user())
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"name\":\"집\",\"recipientName\":\"보호자\",\"recipientPhone\":\"010-0000-0000\",\"postalCode\":\"06236\",\"addressLine1\":\"서울\",\"addressLine2\":null}"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("ADDRESS_NOT_FOUND"));
  }

  @Test
  void subscriptionShippingAllowsLegacyPayloadWithoutName() throws Exception {
    mockMvc
        .perform(
            put("/api/subscriptions/999999/shipping-address")
                .with(user())
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"recipientName\":\"보호자\",\"recipientPhone\":\"010-0000-0000\",\"postalCode\":\"06236\",\"addressLine1\":\"서울\",\"addressLine2\":null}"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("SUBSCRIPTION_NOT_FOUND"));
  }

  private RequestPostProcessor user() {
    return authentication(
        new UsernamePasswordAuthenticationToken(
            new AuthenticatedMemberPrincipal(member.getId(), MemberRole.USER),
            null,
            List.of(new SimpleGrantedAuthority("ROLE_USER"))));
  }
}
