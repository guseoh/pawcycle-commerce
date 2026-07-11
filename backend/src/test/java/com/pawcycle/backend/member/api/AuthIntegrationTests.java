package com.pawcycle.backend.member.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.pawcycle.backend.member.application.AuthenticatedMemberPrincipal;
import com.pawcycle.backend.member.domain.Member;
import com.pawcycle.backend.member.infra.MemberRepository;
import jakarta.servlet.http.Cookie;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class AuthIntegrationTests {

	private final WebApplicationContext applicationContext;
	private final MemberRepository memberRepository;
	private final PasswordEncoder passwordEncoder;
	private final ObjectMapper objectMapper;
	private MockMvc mockMvc;
	private Member member;
	private String password;

	@Autowired
	AuthIntegrationTests(
			WebApplicationContext applicationContext,
			MemberRepository memberRepository,
			PasswordEncoder passwordEncoder,
			ObjectMapper objectMapper) {
		this.applicationContext = applicationContext;
		this.memberRepository = memberRepository;
		this.passwordEncoder = passwordEncoder;
		this.objectMapper = objectMapper;
	}

	@BeforeEach
	void setUp() {
		mockMvc = MockMvcBuilders.webAppContextSetup(applicationContext)
				.apply(springSecurity())
				.build();
		password = "test-password-" + UUID.randomUUID();
		member = memberRepository.saveAndFlush(new Member(
				"Member-" + UUID.randomUUID() + "@example.com", passwordEncoder.encode(password)));
	}

	@Test
	void csrfEndpointReturnsNoStoreTokenForAnonymousSession() throws Exception {
		MvcResult result = mockMvc.perform(get("/api/auth/csrf"))
				.andExpect(status().isOk())
				.andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
				.andExpect(jsonPath("$.token").isNotEmpty())
				.andReturn();

		assertThat(result.getRequest().getSession(false)).isNotNull();
		assertThat(result.getRequest().getRequestURI()).doesNotContain(token(result));
	}

	@Test
	void loginChangesSessionIdPersistsSafePrincipalAndRotatesCsrfToken() throws Exception {
		CsrfSession csrfSession = csrfSession();
		String sessionIdBeforeLogin = csrfSession.session().getId();

		String loginEmail = " \t" + member.getEmail().replace("@example.com", "@EXAMPLE.COM") + "\t ";
		MvcResult login = login(csrfSession, loginEmail, password)
				.andExpect(status().isOk())
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
				.andExpect(jsonPath("$.memberId").value(member.getId()))
				.andExpect(jsonPath("$.email").doesNotExist())
				.andExpect(result -> assertThat(result.getResponse().getRedirectedUrl()).isNull())
				.andReturn();

		assertThat(csrfSession.session().getId()).isNotEqualTo(sessionIdBeforeLogin);
		assertThat(login.getResponse().getContentAsString()).doesNotContain(password);
		assertThat(login.getResponse().getContentAsString()).doesNotContain(member.getPasswordHash());
		assertThat(login.getResponse().getContentAsString()).doesNotContain(csrfSession.session().getId());

		mockMvc.perform(get("/api/auth/me")
					.session(csrfSession.session())
					.param("memberId", String.valueOf(member.getId() + 1))
					.header("X-Member-Id", member.getId() + 1))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.memberId").value(member.getId()));

		String tokenAfterLogin = csrfToken(csrfSession.session());
		assertThat(tokenAfterLogin).isNotEqualTo(csrfSession.token());

		SecurityContext context = (SecurityContext) csrfSession.session().getAttribute(
				HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY);
		assertThat(context.getAuthentication().getPrincipal()).isInstanceOf(AuthenticatedMemberPrincipal.class);
		assertThat(context.getAuthentication().getCredentials()).isNull();
		assertThat(context.getAuthentication().getPrincipal()).isNotInstanceOf(Member.class);
	}

	@Test
	void unknownEmailAndWrongPasswordReturnSameGenericFailureWithoutAuthentication() throws Exception {
		CsrfSession unknownSession = csrfSession();
		MvcResult unknown = login(unknownSession, "unknown@example.com", password)
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"))
				.andExpect(jsonPath("$.fieldErrors").isEmpty())
				.andReturn();

		CsrfSession wrongPasswordSession = csrfSession();
		MvcResult wrongPassword = login(wrongPasswordSession, member.getEmail(), "wrong-" + password)
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"))
				.andExpect(jsonPath("$.fieldErrors").isEmpty())
				.andReturn();

		assertThat(json(unknown).get("message").asText()).isEqualTo(json(wrongPassword).get("message").asText());
		assertThat(unknownSession.session().getAttribute(
				HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY)).isNull();
		assertThat(wrongPasswordSession.session().getAttribute(
				HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY)).isNull();
	}

	@Test
	void validationAndMalformedJsonReturnJsonErrors() throws Exception {
		CsrfSession csrfSession = csrfSession();
		mockMvc.perform(post("/api/auth/login")
					.session(csrfSession.session())
					.header("X-CSRF-TOKEN", csrfSession.token())
					.contentType(MediaType.APPLICATION_JSON)
					.content(objectMapper.writeValueAsString(Map.of("email", "local@@example.com", "password", ""))))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
				.andExpect(jsonPath("$.fieldErrors[*].field").value(java.util.List.of("email", "password")));

		CsrfSession malformedSession = csrfSession();
		mockMvc.perform(post("/api/auth/login")
					.session(malformedSession.session())
					.header("X-CSRF-TOKEN", malformedSession.token())
					.contentType(MediaType.APPLICATION_JSON)
					.content("{"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
				.andExpect(jsonPath("$.fieldErrors").isArray());
	}

	@Test
	void logoutInvalidatesSessionClearsCookieAndRejectsOldSessionAndToken() throws Exception {
		CsrfSession csrfSession = csrfSession();
		login(csrfSession, member.getEmail(), password).andExpect(status().isOk());
		String authenticatedSessionId = csrfSession.session().getId();
		String logoutToken = csrfToken(csrfSession.session());

		mockMvc.perform(post("/api/auth/logout")
					.session(csrfSession.session())
					.header("X-CSRF-TOKEN", logoutToken))
				.andExpect(status().isNoContent())
				.andExpect(content().string(""))
				.andExpect(cookie().maxAge("JSESSIONID", 0));

		assertThat(csrfSession.session().isInvalid()).isTrue();
		mockMvc.perform(get("/api/auth/me").cookie(new Cookie("JSESSIONID", authenticatedSessionId)))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value("AUTH_REQUIRED"));
		mockMvc.perform(post("/api/auth/logout")
					.cookie(new Cookie("JSESSIONID", authenticatedSessionId))
					.header("X-CSRF-TOKEN", logoutToken))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.code").value("CSRF_INVALID"));
	}

	@Test
	void anonymousLogoutWithValidCsrfIsUnauthorizedAndMissingCsrfIsForbidden() throws Exception {
		CsrfSession csrfSession = csrfSession();
		mockMvc.perform(post("/api/auth/logout")
					.session(csrfSession.session())
					.header("X-CSRF-TOKEN", csrfSession.token()))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value("AUTH_REQUIRED"));

		mockMvc.perform(post("/api/auth/logout"))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.code").value("CSRF_INVALID"));
	}

	private CsrfSession csrfSession() throws Exception {
		MvcResult result = mockMvc.perform(get("/api/auth/csrf")).andExpect(status().isOk()).andReturn();
		return new CsrfSession((MockHttpSession) result.getRequest().getSession(false), token(result));
	}

	private String csrfToken(MockHttpSession session) throws Exception {
		MvcResult result = mockMvc.perform(get("/api/auth/csrf").session(session))
				.andExpect(status().isOk())
				.andReturn();
		return token(result);
	}

	private org.springframework.test.web.servlet.ResultActions login(
			CsrfSession csrfSession,
			String email,
			String rawPassword) throws Exception {
		return mockMvc.perform(post("/api/auth/login")
				.session(csrfSession.session())
				.header("X-CSRF-TOKEN", csrfSession.token())
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(new LoginRequest(email, rawPassword))));
	}

	private String token(MvcResult result) throws Exception {
		return json(result).get("token").asText();
	}

	private JsonNode json(MvcResult result) throws Exception {
		return objectMapper.readTree(result.getResponse().getContentAsByteArray());
	}

	private record CsrfSession(MockHttpSession session, String token) {
	}
}
