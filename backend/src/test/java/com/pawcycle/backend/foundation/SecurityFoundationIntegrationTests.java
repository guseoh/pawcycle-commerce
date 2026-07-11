package com.pawcycle.backend.foundation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.pawcycle.backend.common.security.ApiAccessDeniedHandler;
import jakarta.servlet.http.HttpServletResponse;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

@SpringBootTest
@ActiveProfiles("test")
class SecurityFoundationIntegrationTests {

	private final WebApplicationContext applicationContext;
	private final ApiAccessDeniedHandler accessDeniedHandler;
	private MockMvc mockMvc;

	@Autowired
	SecurityFoundationIntegrationTests(
			WebApplicationContext applicationContext,
			ApiAccessDeniedHandler accessDeniedHandler) {
		this.applicationContext = applicationContext;
		this.accessDeniedHandler = accessDeniedHandler;
	}

	@BeforeEach
	void configureMockMvc() {
		mockMvc = MockMvcBuilders.webAppContextSetup(applicationContext)
				.apply(springSecurity())
				.build();
	}

	@Test
	void publicProductAndAuthenticationBoundariesAllowAnonymousRequests() throws Exception {
		mockMvc.perform(get("/api/products/test"))
				.andExpect(status().isNotFound());

		mockMvc.perform(get("/api/auth/csrf"))
				.andExpect(status().isNotFound());
	}

	@Test
	void protectedApiReturnsAuthRequiredJsonWithoutRedirect() throws Exception {
		mockMvc.perform(get("/api/auth/me"))
				.andExpect(status().isUnauthorized())
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
				.andExpect(jsonPath("$.code").value("AUTH_REQUIRED"))
				.andExpect(jsonPath("$.fieldErrors").isArray())
				.andExpect(jsonPath("$.fieldErrors").isEmpty())
				.andExpect(result -> assertThat(result.getResponse().getRedirectedUrl()).isNull());
	}

	@Test
	void anonymousLogoutWithValidCsrfReturnsAuthRequiredJsonWithoutRedirect() throws Exception {
		mockMvc.perform(post("/api/auth/logout").with(csrf()))
				.andExpect(status().isUnauthorized())
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
				.andExpect(jsonPath("$.code").value("AUTH_REQUIRED"))
				.andExpect(jsonPath("$.fieldErrors").isArray())
				.andExpect(jsonPath("$.fieldErrors").isEmpty())
				.andExpect(result -> assertThat(result.getResponse().getRedirectedUrl()).isNull());
	}

	@Test
	void authenticatedRequestCanReachProtectedBoundary() throws Exception {
		mockMvc.perform(get("/api/auth/me").with(user("foundation-user")))
				.andExpect(status().isNotFound());
	}

	@Test
	void stateChangingRequestWithoutCsrfReturnsCsrfInvalidJson() throws Exception {
		mockMvc.perform(post("/api/auth/login"))
				.andExpect(status().isForbidden())
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
				.andExpect(jsonPath("$.code").value("CSRF_INVALID"))
				.andExpect(jsonPath("$.fieldErrors").isArray())
				.andExpect(jsonPath("$.fieldErrors").isEmpty());
	}

	@Test
	void accessDeniedHandlerReturnsAccessDeniedJson() throws Exception {
		MockHttpServletRequest request = new MockHttpServletRequest();
		MockHttpServletResponse response = new MockHttpServletResponse();

		accessDeniedHandler.handle(request, response, new AccessDeniedException("test-only"));

		assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_FORBIDDEN);
		assertThat(response.getContentType()).startsWith(MediaType.APPLICATION_JSON_VALUE);
		assertThat(response.getContentAsString(StandardCharsets.UTF_8)).contains("\"code\":\"ACCESS_DENIED\"");
		assertThat(response.getContentAsString(StandardCharsets.UTF_8)).contains("\"fieldErrors\":[]");
	}

}
