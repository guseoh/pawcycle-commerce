package com.pawcycle.backend.foundation;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;
import org.junit.jupiter.api.Test;

class SessionCookieConfigurationTests {

	@Test
	void productionDefaultsAreSecureAndLocalTestOverridesAreExplicit() throws IOException {
		Properties defaults = load("application.properties");
		Properties localExample = load("application-local.example.properties");
		Properties test = load("application-test.properties");

		assertThat(defaults.getProperty("server.servlet.session.cookie.name")).isEqualTo("JSESSIONID");
		assertThat(defaults.getProperty("server.servlet.session.cookie.http-only")).isEqualTo("true");
		assertThat(defaults.getProperty("server.servlet.session.cookie.same-site")).isEqualTo("lax");
		assertThat(defaults.getProperty("server.servlet.session.cookie.secure"))
				.isEqualTo("${SESSION_COOKIE_SECURE:true}");
		assertThat(defaults.getProperty("server.servlet.session.cookie.path")).isEqualTo("/");
		assertThat(defaults.getProperty("server.servlet.session.timeout")).isEqualTo("30m");
		assertThat(defaults).doesNotContainKey("server.servlet.session.cookie.domain");
		assertThat(defaults).doesNotContainKey("server.servlet.session.cookie.max-age");
		assertThat(localExample.getProperty("server.servlet.session.cookie.secure")).isEqualTo("false");
		assertThat(test.getProperty("server.servlet.session.cookie.secure")).isEqualTo("false");
	}

	private Properties load(String name) throws IOException {
		Properties properties = new Properties();
		try (InputStream input = Thread.currentThread().getContextClassLoader().getResourceAsStream(name)) {
			assertThat(input).as(name).isNotNull();
			properties.load(input);
		}
		return properties;
	}
}
