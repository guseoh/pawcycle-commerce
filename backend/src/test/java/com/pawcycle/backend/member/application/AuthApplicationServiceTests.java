package com.pawcycle.backend.member.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.pawcycle.backend.member.domain.MemberRole;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.logout.LogoutHandler;
import org.springframework.security.web.authentication.session.SessionAuthenticationStrategy;
import org.springframework.security.web.context.SecurityContextRepository;

class AuthApplicationServiceTests {

	private final EmailNormalizer emailNormalizer = new EmailNormalizer();
	private final MemberCredentialAuthenticator credentialAuthenticator = mock(MemberCredentialAuthenticator.class);
	private final SessionAuthenticationStrategy sessionAuthenticationStrategy =
			mock(SessionAuthenticationStrategy.class);
	private final SecurityContextRepository securityContextRepository = mock(SecurityContextRepository.class);
	private final LogoutHandler logoutHandler = mock(LogoutHandler.class);
	private final HttpServletRequest request = mock(HttpServletRequest.class);
	private final HttpServletResponse response = mock(HttpServletResponse.class);
	private AuthApplicationService authApplicationService;

	@BeforeEach
	void setUp() {
		authApplicationService = new AuthApplicationService(
				emailNormalizer,
				credentialAuthenticator,
				sessionAuthenticationStrategy,
				securityContextRepository,
				logoutHandler);
	}

	@AfterEach
	void clearSecurityContext() {
		SecurityContextHolder.clearContext();
	}

	@Test
	void invalidCredentialsDoNotStartTheAuthenticatedSessionLifecycle() {
		when(credentialAuthenticator.authenticate("unknown@example.com", "presented-password"))
				.thenThrow(new InvalidCredentialsException());

		assertThatThrownBy(() -> authApplicationService.login(
				"unknown@example.com", "presented-password", request, response))
				.isInstanceOf(InvalidCredentialsException.class);

		verifyNoInteractions(sessionAuthenticationStrategy, securityContextRepository);
	}

	@Test
	void successfulAuthenticationStartsSessionThenPersistsTheSecurityContext() {
		AuthenticatedMemberPrincipal principal = new AuthenticatedMemberPrincipal(7L, MemberRole.ADMIN);
		Authentication authentication = UsernamePasswordAuthenticationToken.authenticated(principal, null, List.of());
		when(credentialAuthenticator.authenticate("member@example.com", "presented-password"))
				.thenReturn(authentication);

		assertThat(authApplicationService.login("member@example.com", "presented-password", request, response))
				.isEqualTo(principal);

		verify(sessionAuthenticationStrategy).onAuthentication(authentication, request, response);
		verify(securityContextRepository).saveContext(
				SecurityContextHolder.getContext(), request, response);
		assertThat(SecurityContextHolder.getContext().getAuthentication()).isSameAs(authentication);
	}

	@Test
	void logoutDelegatesOnlyToTheConfiguredLogoutLifecycle() {
		authApplicationService.logout(request, response);

		verify(logoutHandler).logout(request, response, null);
	}
}
