package com.pawcycle.backend.commerce;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.pawcycle.backend.member.application.AuthenticatedMemberPrincipal;
import com.pawcycle.backend.member.domain.MemberRole;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

@SpringBootTest
@ActiveProfiles("test")
class CommerceAdminAuthorizationIntegrationTests {
	private final WebApplicationContext applicationContext;
	private MockMvc mockMvc;

	CommerceAdminAuthorizationIntegrationTests(WebApplicationContext applicationContext) {
		this.applicationContext = applicationContext;
	}

	@BeforeEach
	void setUp() {
		mockMvc = MockMvcBuilders.webAppContextSetup(applicationContext).apply(springSecurity()).build();
	}

	@Test
	void operationsReadKeepsAnonymousUserAndMemberBoundaries() throws Exception {
		mockMvc.perform(get("/api/admin/operations"))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value("AUTH_REQUIRED"));
		mockMvc.perform(get("/api/admin/operations").with(role(MemberRole.USER)))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
		mockMvc.perform(get("/api/admin/operations").with(role(MemberRole.ADMIN)))
				.andExpect(status().isOk());
	}

	@Test
	void requestLengthsUseBeanValidationBeforeAdminMutation() throws Exception {
		mockMvc.perform(post("/api/admin/deliveries/1/fail").with(role(MemberRole.ADMIN)).with(csrf())
				.contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"" + "x".repeat(501) + "\"}"))
				.andExpect(status().isBadRequest());
		mockMvc.perform(post("/api/admin/deliveries/1/ship").with(role(MemberRole.ADMIN)).with(csrf())
				.contentType(MediaType.APPLICATION_JSON).content("{\"carrierCode\":\"" + "x".repeat(51) + "\",\"trackingNumber\":\"ok\"}"))
				.andExpect(status().isBadRequest());
		mockMvc.perform(post("/api/admin/deliveries/1/ship").with(role(MemberRole.ADMIN)).with(csrf())
				.contentType(MediaType.APPLICATION_JSON).content("{\"carrierCode\":\"ok\",\"trackingNumber\":\"" + "x".repeat(101) + "\"}"))
				.andExpect(status().isBadRequest());
	}

	@ParameterizedTest
	@MethodSource("adminMutations")
	void adminMutationsRequireCsrfAndAllowOnlyAdmin(String path, String body) throws Exception {
		mockMvc.perform(post(path).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(body))
				.andExpect(status().isUnauthorized());
		mockMvc.perform(post(path).with(role(MemberRole.USER)).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(body))
				.andExpect(status().isForbidden());
		mockMvc.perform(post(path).with(role(MemberRole.ADMIN)).contentType(MediaType.APPLICATION_JSON).content(body))
				.andExpect(status().isForbidden());
		mockMvc.perform(post(path).with(role(MemberRole.ADMIN)).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(body))
				.andExpect(result -> {
					int status = result.getResponse().getStatus();
					if (status == 401 || status == 403) throw new AssertionError("admin mutation was rejected by security: " + path);
				});
	}

	static Stream<Arguments> adminMutations() {
		return Stream.of(
				Arguments.of("/api/admin/deliveries/999999/complete", "{}"),
				Arguments.of("/api/admin/returns/999999/approve", "{}"),
				Arguments.of("/api/admin/refunds/999999/process", "{}"),
				Arguments.of("/api/admin/payments/999999/reconcile", "{}"),
				Arguments.of("/api/admin/payments/999999/retry-billing", "{}"),
				Arguments.of("/api/admin/inventories/999999/adjustments", "{\"delta\":1}"));
	}

	private RequestPostProcessor role(MemberRole role) {
		return authentication(new UsernamePasswordAuthenticationToken(
				new AuthenticatedMemberPrincipal(1L, role),
				null,
				List.of(new SimpleGrantedAuthority("ROLE_" + role.name()))));
	}
}
