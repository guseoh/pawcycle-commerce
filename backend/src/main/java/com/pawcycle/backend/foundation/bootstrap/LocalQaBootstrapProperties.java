package com.pawcycle.backend.foundation.bootstrap;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("pawcycle.local-qa-bootstrap")
public record LocalQaBootstrapProperties(
		boolean enabled,
		boolean resetSubscriptions,
		String email,
		String password) {
}
