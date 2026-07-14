package com.pawcycle.backend.foundation.bootstrap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class LocalQaBootstrapConfigurationTests {

	private final LocalQaBootstrapService bootstrapService = mock(LocalQaBootstrapService.class);
	private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
			.withUserConfiguration(LocalQaBootstrapConfiguration.class)
			.withBean(LocalQaBootstrapService.class, () -> bootstrapService);

	@Test
	void defaultAndNonLocalProfilesDoNotCreateBootstrapRunner() {
		contextRunner.run(context -> assertThat(context).doesNotHaveBean("localQaBootstrapRunner"));

		contextRunner
				.withPropertyValues(
						"spring.profiles.active=test",
						"pawcycle.local-qa-bootstrap.enabled=true")
				.run(context -> assertThat(context).doesNotHaveBean("localQaBootstrapRunner"));

		contextRunner
				.withPropertyValues(
						"spring.profiles.active=production",
						"pawcycle.local-qa-bootstrap.enabled=true")
				.run(context -> assertThat(context).doesNotHaveBean("localQaBootstrapRunner"));
	}

	@Test
	void localProfileStillRequiresExplicitEnablement() {
		contextRunner
				.withPropertyValues("spring.profiles.active=local-integration")
				.run(context -> assertThat(context).doesNotHaveBean("localQaBootstrapRunner"));
	}

	@Test
	void productionOrTestProfileBlocksRunnerEvenWithLocalProfile() {
		contextRunner
				.withPropertyValues(
						"spring.profiles.active=local-integration,production",
						"pawcycle.local-qa-bootstrap.enabled=true")
				.run(context -> assertThat(context).doesNotHaveBean("localQaBootstrapRunner"));

		contextRunner
				.withPropertyValues(
						"spring.profiles.active=local-integration,test",
						"pawcycle.local-qa-bootstrap.enabled=true")
				.run(context -> assertThat(context).doesNotHaveBean("localQaBootstrapRunner"));
	}

	@Test
	void localIntegrationProfileDoesNotOverrideSessionCookieSecurity() throws IOException {
		Properties properties = new Properties();
		try (InputStream input = getClass().getResourceAsStream("/application-local-integration.properties")) {
			assertThat(input).isNotNull();
			properties.load(input);
		}

		assertThat(properties).doesNotContainKey("server.servlet.session.cookie.secure");
	}

	@Test
	void enabledLocalRunnerPassesPropertiesToService() {
		String email = runtimeQaEmail();
		String password = UUID.randomUUID().toString();

		contextRunner
				.withPropertyValues(
						"spring.profiles.active=local-integration",
						"pawcycle.local-qa-bootstrap.enabled=true",
						"pawcycle.local-qa-bootstrap.reset-subscriptions=true",
						"pawcycle.local-qa-bootstrap.email=" + email,
						"pawcycle.local-qa-bootstrap.password=" + password)
				.run(context -> {
					ApplicationRunner runner = context.getBean("localQaBootstrapRunner", ApplicationRunner.class);
					runner.run(null);
					verify(bootstrapService).bootstrap(email, password, true);
				});
	}

	@Test
	void runnerPropagatesBootstrapFailureAndStopsStartup() {
		String email = runtimeQaEmail();
		String password = UUID.randomUUID().toString();
		doThrow(new LocalQaBootstrapException("로컬 QA bootstrap 설정 오류"))
				.when(bootstrapService).bootstrap(email, password, false);

		contextRunner
				.withPropertyValues(
						"spring.profiles.active=local-integration",
						"pawcycle.local-qa-bootstrap.enabled=true",
						"pawcycle.local-qa-bootstrap.email=" + email,
						"pawcycle.local-qa-bootstrap.password=" + password)
				.run(context -> {
					ApplicationRunner runner = context.getBean("localQaBootstrapRunner", ApplicationRunner.class);
					assertThatThrownBy(() -> runner.run(null))
							.isInstanceOf(LocalQaBootstrapException.class);
				});
	}

	private String runtimeQaEmail() {
		return LocalQaBootstrapService.QA_EMAIL_LOCAL_PART + "@" + UUID.randomUUID() + ".example";
	}
}
