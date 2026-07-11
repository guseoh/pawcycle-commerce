package com.pawcycle.backend.common.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.csrf.HttpSessionCsrfTokenRepository;

@Configuration
public class SecurityConfig {

	@Bean
	SecurityFilterChain securityFilterChain(
			HttpSecurity http,
			ApiAuthenticationEntryPoint authenticationEntryPoint,
			ApiAccessDeniedHandler accessDeniedHandler,
			HttpSessionCsrfTokenRepository csrfTokenRepository) throws Exception {
		http
				.csrf(csrf -> csrf.csrfTokenRepository(csrfTokenRepository))
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
						.permitAll())
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
}
