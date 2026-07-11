package com.pawcycle.backend.common.security;

import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.logout.CompositeLogoutHandler;
import org.springframework.security.web.authentication.logout.CookieClearingLogoutHandler;
import org.springframework.security.web.authentication.logout.LogoutHandler;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.security.web.authentication.session.ChangeSessionIdAuthenticationStrategy;
import org.springframework.security.web.authentication.session.CompositeSessionAuthenticationStrategy;
import org.springframework.security.web.authentication.session.SessionAuthenticationStrategy;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CsrfAuthenticationStrategy;
import org.springframework.security.web.csrf.CsrfLogoutHandler;
import org.springframework.security.web.csrf.HttpSessionCsrfTokenRepository;

@Configuration
public class SecurityConfig {

	@Bean
	SecurityFilterChain securityFilterChain(
			HttpSecurity http,
			ApiAuthenticationEntryPoint authenticationEntryPoint,
			ApiAccessDeniedHandler accessDeniedHandler,
			HttpSessionCsrfTokenRepository csrfTokenRepository,
			SecurityContextRepository securityContextRepository) throws Exception {
		http
				.csrf(csrf -> csrf.csrfTokenRepository(csrfTokenRepository))
				.securityContext(context -> context.securityContextRepository(securityContextRepository))
				.authorizeHttpRequests(authorize -> authorize
						.requestMatchers(HttpMethod.GET, "/api/products", "/api/products/**", "/api/auth/csrf")
						.permitAll()
						.requestMatchers(HttpMethod.POST, "/api/auth/login")
						.permitAll()
						.requestMatchers(HttpMethod.POST, "/api/auth/logout")
						.authenticated()
						.requestMatchers(HttpMethod.GET, "/api/auth/me")
						.authenticated()
						.requestMatchers("/api/**")
						.authenticated()
						.anyRequest()
						.denyAll())
				.exceptionHandling(exceptions -> exceptions
						.authenticationEntryPoint(authenticationEntryPoint)
						.accessDeniedHandler(accessDeniedHandler))
				.sessionManagement(session -> session.sessionFixation(fixation -> fixation.changeSessionId()));
		return http.build();
	}

	@Bean
	HttpSessionCsrfTokenRepository csrfTokenRepository() {
		HttpSessionCsrfTokenRepository repository = new HttpSessionCsrfTokenRepository();
		repository.setHeaderName("X-CSRF-TOKEN");
		repository.setParameterName("_csrf");
		return repository;
	}

	@Bean
	PasswordEncoder passwordEncoder() {
		return new BCryptPasswordEncoder();
	}

	@Bean
	SessionAuthenticationStrategy sessionAuthenticationStrategy(
			HttpSessionCsrfTokenRepository csrfTokenRepository) {
		return new CompositeSessionAuthenticationStrategy(List.of(
				new ChangeSessionIdAuthenticationStrategy(),
				new CsrfAuthenticationStrategy(csrfTokenRepository)));
	}

	@Bean
	SecurityContextRepository securityContextRepository() {
		return new HttpSessionSecurityContextRepository();
	}

	@Bean
	LogoutHandler logoutHandler(HttpSessionCsrfTokenRepository csrfTokenRepository) {
		return new CompositeLogoutHandler(
				new CsrfLogoutHandler(csrfTokenRepository),
				new SecurityContextLogoutHandler(),
				new CookieClearingLogoutHandler("JSESSIONID"));
	}
}
