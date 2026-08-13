package com.pawcycle.backend.member.application;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.logout.LogoutHandler;
import org.springframework.security.web.authentication.session.SessionAuthenticationStrategy;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthApplicationService {

	private final EmailNormalizer emailNormalizer;
	private final MemberCredentialAuthenticator credentialAuthenticator;
	private final SessionAuthenticationStrategy sessionAuthenticationStrategy;
	private final SecurityContextRepository securityContextRepository;
	private final LogoutHandler logoutHandler;

	public AuthApplicationService(
			EmailNormalizer emailNormalizer,
			MemberCredentialAuthenticator credentialAuthenticator,
			SessionAuthenticationStrategy sessionAuthenticationStrategy,
			SecurityContextRepository securityContextRepository,
			LogoutHandler logoutHandler) {
		this.emailNormalizer = emailNormalizer;
		this.credentialAuthenticator = credentialAuthenticator;
		this.sessionAuthenticationStrategy = sessionAuthenticationStrategy;
		this.securityContextRepository = securityContextRepository;
		this.logoutHandler = logoutHandler;
	}

	@Transactional(readOnly = true)
	public AuthenticatedMemberPrincipal login(
			String email,
			String password,
			HttpServletRequest request,
			HttpServletResponse response) {
		NormalizedLoginCredentials credentials = emailNormalizer.normalize(email, password);
		Authentication authentication = credentialAuthenticator.authenticate(credentials.email(), credentials.password());
		sessionAuthenticationStrategy.onAuthentication(authentication, request, response);

		SecurityContext context = SecurityContextHolder.createEmptyContext();
		context.setAuthentication(authentication);
		SecurityContextHolder.setContext(context);
		securityContextRepository.saveContext(context, request, response);
		return (AuthenticatedMemberPrincipal) authentication.getPrincipal();
	}

	public void logout(HttpServletRequest request, HttpServletResponse response) {
		logoutHandler.logout(request, response, SecurityContextHolder.getContext().getAuthentication());
	}
}
