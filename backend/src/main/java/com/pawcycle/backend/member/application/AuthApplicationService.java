package com.pawcycle.backend.member.application;

import com.pawcycle.backend.member.domain.Member;
import com.pawcycle.backend.member.infra.MemberRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.authentication.logout.LogoutHandler;
import org.springframework.security.web.authentication.session.SessionAuthenticationStrategy;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthApplicationService {

	private final EmailNormalizer emailNormalizer;
	private final MemberRepository memberRepository;
	private final PasswordEncoder passwordEncoder;
	private final String dummyPasswordHash;
	private final SessionAuthenticationStrategy sessionAuthenticationStrategy;
	private final SecurityContextRepository securityContextRepository;
	private final LogoutHandler logoutHandler;

	public AuthApplicationService(
			EmailNormalizer emailNormalizer,
			MemberRepository memberRepository,
			PasswordEncoder passwordEncoder,
			SessionAuthenticationStrategy sessionAuthenticationStrategy,
			SecurityContextRepository securityContextRepository,
			LogoutHandler logoutHandler) {
		this.emailNormalizer = emailNormalizer;
		this.memberRepository = memberRepository;
		this.passwordEncoder = passwordEncoder;
		this.dummyPasswordHash = passwordEncoder.encode(UUID.randomUUID().toString());
		this.sessionAuthenticationStrategy = sessionAuthenticationStrategy;
		this.securityContextRepository = securityContextRepository;
		this.logoutHandler = logoutHandler;
	}

	@Transactional(readOnly = true)
	public Long login(
			String email,
			String password,
			HttpServletRequest request,
			HttpServletResponse response) {
		NormalizedLoginCredentials credentials = emailNormalizer.normalize(email, password);
		Optional<Member> candidate = memberRepository.findByEmail(credentials.email());
		String passwordHash = candidate.map(Member::getPasswordHash).orElse(dummyPasswordHash);
		boolean passwordMatches = passwordEncoder.matches(credentials.password(), passwordHash);
		if (candidate.isEmpty() || !passwordMatches) {
			throw new InvalidCredentialsException();
		}
		Member member = candidate.get();

		AuthenticatedMemberPrincipal principal = new AuthenticatedMemberPrincipal(member.getId());
		Authentication authentication = new UsernamePasswordAuthenticationToken(principal, null, List.of());
		sessionAuthenticationStrategy.onAuthentication(authentication, request, response);

		SecurityContext context = SecurityContextHolder.createEmptyContext();
		context.setAuthentication(authentication);
		SecurityContextHolder.setContext(context);
		securityContextRepository.saveContext(context, request, response);
		return principal.memberId();
	}

	public void logout(HttpServletRequest request, HttpServletResponse response) {
		logoutHandler.logout(request, response, SecurityContextHolder.getContext().getAuthentication());
	}
}
