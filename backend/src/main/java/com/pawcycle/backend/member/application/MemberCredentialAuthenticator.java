package com.pawcycle.backend.member.application;

import java.util.Objects;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

/** Adapts login credentials to Spring Security's AuthenticationManager boundary. */
@Component
public class MemberCredentialAuthenticator {
	private final AuthenticationManager authenticationManager;

	public MemberCredentialAuthenticator(AuthenticationManager authenticationManager) {
		this.authenticationManager = authenticationManager;
	}

	public Authentication authenticate(String email, String password) {
		return authenticationManager.authenticate(
				UsernamePasswordAuthenticationToken.unauthenticated(
						Objects.requireNonNull(email), Objects.requireNonNull(password)));
	}
}
