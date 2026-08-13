package com.pawcycle.backend.member.application;

import com.pawcycle.backend.member.domain.Member;
import com.pawcycle.backend.member.infra.MemberRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/** Verifies member credentials and returns only the session-safe principal. */
@Component
public class MemberAuthenticationProvider implements AuthenticationProvider {
	private final MemberRepository memberRepository;
	private final PasswordEncoder passwordEncoder;
	private final String dummyPasswordHash;

	public MemberAuthenticationProvider(MemberRepository memberRepository, PasswordEncoder passwordEncoder) {
		this.memberRepository = memberRepository;
		this.passwordEncoder = passwordEncoder;
		this.dummyPasswordHash = passwordEncoder.encode(UUID.randomUUID().toString());
	}

	@Override
	public Authentication authenticate(Authentication authentication) {
		String email = (String) authentication.getPrincipal();
		String password = (String) authentication.getCredentials();
		Optional<Member> candidate = memberRepository.findByEmail(email);
		String passwordHash = candidate.map(Member::getPasswordHash).orElse(dummyPasswordHash);
		boolean passwordMatches = passwordEncoder.matches(password, passwordHash);
		if (candidate.isEmpty() || !passwordMatches) {
			throw new InvalidCredentialsException();
		}
		Member member = candidate.get();
		return UsernamePasswordAuthenticationToken.authenticated(
				new AuthenticatedMemberPrincipal(member.getId(), member.getRole()),
				null,
				List.of(new SimpleGrantedAuthority("ROLE_" + member.getRole().name())));
	}

	@Override
	public boolean supports(Class<?> authentication) {
		return UsernamePasswordAuthenticationToken.class.isAssignableFrom(authentication);
	}
}
