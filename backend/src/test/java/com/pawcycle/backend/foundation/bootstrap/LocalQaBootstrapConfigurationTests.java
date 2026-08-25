package com.pawcycle.backend.foundation.bootstrap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

class LocalQaBootstrapConfigurationTests {

	private final LocalQaBootstrapService bootstrapService = mock(LocalQaBootstrapService.class);
	private final LocalQaMvp2FixtureService mvp2FixtureService = mock(LocalQaMvp2FixtureService.class);
	private final LocalCommerceDemoFixtureService commerceDemoFixtureService = mock(LocalCommerceDemoFixtureService.class);
	private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
			.withUserConfiguration(LocalQaBootstrapConfiguration.class)
			.withBean(LocalQaBootstrapService.class, () -> bootstrapService)
				.withBean(LocalQaMvp2FixtureService.class, () -> mvp2FixtureService)
				.withBean(LocalCommerceDemoFixtureService.class, () -> commerceDemoFixtureService);
	private final ApplicationContextRunner demoFixtureContextRunner = new ApplicationContextRunner()
			.withUserConfiguration(DemoFixtureConfiguration.class);

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

		contextRunner
				.withPropertyValues(
						"spring.profiles.active=local-integration,prod",
						"pawcycle.local-qa-bootstrap.enabled=true")
				.run(context -> assertThat(context).doesNotHaveBean("localQaBootstrapRunner"));
	}

	@Test
	void demoFixtureServiceIsNotCreatedOutsideLocalIntegrationProfile() {
		demoFixtureContextRunner
				.withPropertyValues("spring.profiles.active=test")
				.run(context -> assertThat(context).doesNotHaveBean(LocalCommerceDemoFixtureService.class));

		demoFixtureContextRunner
				.withPropertyValues("spring.profiles.active=production")
				.run(context -> assertThat(context).doesNotHaveBean(LocalCommerceDemoFixtureService.class));
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
	void enabledLocalRunnerPassesPropertiesAndBootstrapsMvp2Fixture() {
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
					context.getBean("localDemoCatalogBootstrapRunner", ApplicationRunner.class).run(null);
					verify(bootstrapService).bootstrap(email, password, true);
					verify(mvp2FixtureService).bootstrap();
					verify(commerceDemoFixtureService).bootstrap();
				});
	}

	@Test
	void runnerPropagatesBootstrapFailureAndDoesNotCreateMvp2Fixture() {
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
					verifyNoInteractions(mvp2FixtureService);
				});
	}

	@Test
	void runnerPropagatesMvp2FixtureFailureAndStopsStartup() {
		String email = runtimeQaEmail();
		String password = UUID.randomUUID().toString();
		doThrow(new LocalQaBootstrapException("MVP2 fixture 설정 오류"))
				.when(mvp2FixtureService).bootstrap();

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
					verify(bootstrapService).bootstrap(email, password, false);
				});
	}

	@Test
	void runnerPropagatesDemoFixtureFailureAfterExistingFixtures() {
		String email = runtimeQaEmail();
		String password = UUID.randomUUID().toString();
		doThrow(new LocalQaBootstrapException("Commerce Demo fixture 설정 오류"))
				.when(commerceDemoFixtureService).bootstrap();

		contextRunner
				.withPropertyValues(
						"spring.profiles.active=local-integration",
						"pawcycle.local-qa-bootstrap.enabled=true",
						"pawcycle.local-qa-bootstrap.email=" + email,
						"pawcycle.local-qa-bootstrap.password=" + password)
				.run(context -> {
					ApplicationRunner runner = context.getBean("localQaBootstrapRunner", ApplicationRunner.class);
					runner.run(null);
					ApplicationRunner demoRunner = context.getBean("localDemoCatalogBootstrapRunner", ApplicationRunner.class);
					assertThatThrownBy(() -> demoRunner.run(null))
							.isInstanceOf(LocalQaBootstrapException.class);
					verify(bootstrapService).bootstrap(email, password, false);
					verify(mvp2FixtureService).bootstrap();
					verify(commerceDemoFixtureService).bootstrap();
				});
	}

	private String runtimeQaEmail() {
		return LocalQaBootstrapService.QA_EMAIL_LOCAL_PART + "@" + UUID.randomUUID() + ".example";
	}

	@Configuration(proxyBeanMethods = false)
	@Import(LocalCommerceDemoFixtureService.class)
	static class DemoFixtureConfiguration {}
}
