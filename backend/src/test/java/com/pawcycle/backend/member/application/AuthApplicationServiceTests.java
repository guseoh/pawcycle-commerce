package com.pawcycle.backend.member.application;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.pawcycle.backend.member.domain.Member;
import com.pawcycle.backend.member.infra.MemberRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.authentication.logout.LogoutHandler;
import org.springframework.security.web.authentication.session.SessionAuthenticationStrategy;
import org.springframework.security.web.context.SecurityContextRepository;

class AuthApplicationServiceTests {

	private static final String DUMMY_PASSWORD_HASH = "dummy-password-hash";
	private static final String PRESENTED_PASSWORD = "presented-password";

	private final EmailNormalizer emailNormalizer = new EmailNormalizer();
	private final MemberRepository memberRepository = mock(MemberRepository.class);
	private final PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
	private final SessionAuthenticationStrategy sessionAuthenticationStrategy =
			mock(SessionAuthenticationStrategy.class);
	private final SecurityContextRepository securityContextRepository = mock(SecurityContextRepository.class);
	private final LogoutHandler logoutHandler = mock(LogoutHandler.class);
	private final HttpServletRequest request = mock(HttpServletRequest.class);
	private final HttpServletResponse response = mock(HttpServletResponse.class);
	private AuthApplicationService authApplicationService;

	@BeforeEach
	void setUp() {
		when(passwordEncoder.encode(anyString())).thenReturn(DUMMY_PASSWORD_HASH);
		authApplicationService = new AuthApplicationService(
				emailNormalizer,
				memberRepository,
				passwordEncoder,
				sessionAuthenticationStrategy,
				securityContextRepository,
				logoutHandler);
	}

	@Test
	void unknownEmailPerformsExactlyOnePasswordComparisonWithoutAuthentication() {
		when(memberRepository.findByEmail("unknown@example.com")).thenReturn(Optional.empty());

		assertThatThrownBy(() -> authApplicationService.login(
				"unknown@example.com", PRESENTED_PASSWORD, request, response))
				.isInstanceOf(InvalidCredentialsException.class);

		verify(passwordEncoder, times(1)).matches(PRESENTED_PASSWORD, DUMMY_PASSWORD_HASH);
		verify(passwordEncoder, times(1)).encode(anyString());
		verifyNoMoreInteractions(passwordEncoder);
		verifyNoInteractions(sessionAuthenticationStrategy, securityContextRepository);
	}

	@Test
	void wrongPasswordPerformsExactlyOnePasswordComparisonWithoutAuthentication() {
		Member member = new Member("member@example.com", "member-password-hash");
		when(memberRepository.findByEmail("member@example.com")).thenReturn(Optional.of(member));
		when(passwordEncoder.matches(PRESENTED_PASSWORD, member.getPasswordHash())).thenReturn(false);

		assertThatThrownBy(() -> authApplicationService.login(
				"member@example.com", PRESENTED_PASSWORD, request, response))
				.isInstanceOf(InvalidCredentialsException.class);

		verify(passwordEncoder, times(1)).matches(PRESENTED_PASSWORD, member.getPasswordHash());
		verify(passwordEncoder, times(1)).encode(anyString());
		verifyNoMoreInteractions(passwordEncoder);
		verifyNoInteractions(sessionAuthenticationStrategy, securityContextRepository);
	}

	@Test
	void reusesDummyHashWithoutEncodingPerRequest() {
		when(memberRepository.findByEmail("unknown@example.com")).thenReturn(Optional.empty());

		assertThatThrownBy(() -> authApplicationService.login(
				"unknown@example.com", PRESENTED_PASSWORD, request, response))
				.isInstanceOf(InvalidCredentialsException.class);
		assertThatThrownBy(() -> authApplicationService.login(
				"unknown@example.com", PRESENTED_PASSWORD, request, response))
				.isInstanceOf(InvalidCredentialsException.class);

		verify(passwordEncoder, times(1)).encode(anyString());
		verify(passwordEncoder, times(2)).matches(PRESENTED_PASSWORD, DUMMY_PASSWORD_HASH);
		verifyNoMoreInteractions(passwordEncoder);
	}
}
