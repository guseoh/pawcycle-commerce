package com.pawcycle.backend.foundation.bootstrap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.pawcycle.backend.catalog.application.DemoProductDetailSectionFixtureService;
import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

class LocalQaBootstrapConfigurationTests {

  private final LocalQaBootstrapService bootstrapService = mock(LocalQaBootstrapService.class);
  private final LocalQaMvp2FixtureService mvp2FixtureService =
      mock(LocalQaMvp2FixtureService.class);
  private final LocalCommerceDemoFixtureService commerceDemoFixtureService =
      mock(LocalCommerceDemoFixtureService.class);
  private final ApplicationContextRunner contextRunner =
      new ApplicationContextRunner()
          .withUserConfiguration(LocalQaBootstrapConfiguration.class)
          .withBean(LocalQaBootstrapService.class, () -> bootstrapService)
          .withBean(LocalQaMvp2FixtureService.class, () -> mvp2FixtureService)
          .withBean(LocalCommerceDemoFixtureService.class, () -> commerceDemoFixtureService);
  private final ApplicationContextRunner demoFixtureContextRunner =
      new ApplicationContextRunner().withUserConfiguration(DemoFixtureConfiguration.class);
  private final ApplicationContextRunner detailFixtureContextRunner =
      new ApplicationContextRunner().withUserConfiguration(DetailFixtureConfiguration.class);

  @Test
  void customerCatalogV3RequiresExplicitLocalEnablementAndBlocksMixedProfiles() {
    var fixture = mock(LocalCustomerCatalogV3FixtureService.class);
    var runner =
        new ApplicationContextRunner()
            .withUserConfiguration(LocalCustomerCatalogV3Configuration.class)
            .withBean(LocalCustomerCatalogV3FixtureService.class, () -> fixture);
    for (String profile :
        java.util.List.of(
            "default",
            "test",
            "production",
            "prod",
            "local-integration,test",
            "local-integration,production",
            "local-integration,prod")) {
      runner
          .withPropertyValues(
              "spring.profiles.active=" + profile,
              "pawcycle.local-customer-catalog-v3.enabled=true")
          .run(context -> assertThat(context).doesNotHaveBean("localCustomerCatalogV3Runner"));
      new ApplicationContextRunner()
          .withUserConfiguration(CustomerV3FixtureConfiguration.class)
          .withPropertyValues("spring.profiles.active=" + profile)
          .run(
              context ->
                  assertThat(context).doesNotHaveBean(LocalCustomerCatalogV3FixtureService.class));
    }
    runner
        .withPropertyValues("spring.profiles.active=local-integration")
        .run(context -> assertThat(context).doesNotHaveBean("localCustomerCatalogV3Runner"));
    runner
        .withPropertyValues(
            "spring.profiles.active=local-integration",
            "pawcycle.local-customer-catalog-v3.enabled=true")
        .run(
            context -> {
              context.getBean("localCustomerCatalogV3Runner", ApplicationRunner.class).run(null);
              verify(fixture).bootstrap();
            });
  }

  @Test
  void customerCatalogV3RejectsSyntheticOverrideOrDisabledBaseline() {
    var fixture = mock(LocalCustomerCatalogV3FixtureService.class);
    var runner =
        new ApplicationContextRunner()
            .withUserConfiguration(LocalCustomerCatalogV3Configuration.class)
            .withBean(LocalCustomerCatalogV3FixtureService.class, () -> fixture)
            .withPropertyValues(
                "spring.profiles.active=local-integration",
                "pawcycle.local-customer-catalog-v3.enabled=true");
    for (String property :
        java.util.List.of(
            "pawcycle.local-demo-catalog.manifest=file:/tmp/synthetic.json",
            "pawcycle.local-demo-catalog.enabled=false")) {
      runner
          .withPropertyValues(property)
          .run(
              context -> {
                assertThatThrownBy(
                        () ->
                            context
                                .getBean("localCustomerCatalogV3Runner", ApplicationRunner.class)
                                .run(null))
                    .isInstanceOf(LocalQaBootstrapException.class);
                verifyNoInteractions(fixture);
              });
    }
  }

  @Test
  void defaultAndNonLocalProfilesDoNotCreateBootstrapRunner() {
    contextRunner.run(context -> assertThat(context).doesNotHaveBean("localQaBootstrapRunner"));

    contextRunner
        .withPropertyValues(
            "spring.profiles.active=test", "pawcycle.local-qa-bootstrap.enabled=true")
        .run(context -> assertThat(context).doesNotHaveBean("localQaBootstrapRunner"));

    contextRunner
        .withPropertyValues(
            "spring.profiles.active=production", "pawcycle.local-qa-bootstrap.enabled=true")
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
  void detailFixtureServiceIsNotCreatedInTestOrProductionProfile() {
    detailFixtureContextRunner
        .withPropertyValues("spring.profiles.active=test")
        .run(
            context ->
                assertThat(context).doesNotHaveBean(DemoProductDetailSectionFixtureService.class));

    detailFixtureContextRunner
        .withPropertyValues("spring.profiles.active=production")
        .run(
            context ->
                assertThat(context).doesNotHaveBean(DemoProductDetailSectionFixtureService.class));
  }

  @Test
  void localIntegrationProfileDoesNotOverrideSessionCookieSecurity() throws IOException {
    Properties properties = new Properties();
    try (InputStream input =
        getClass().getResourceAsStream("/application-local-integration.properties")) {
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
        .run(
            context -> {
              ApplicationRunner runner =
                  context.getBean("localQaBootstrapRunner", ApplicationRunner.class);
              runner.run(null);
              context.getBean("localDemoCatalogBootstrapRunner", ApplicationRunner.class).run(null);
              verify(bootstrapService).bootstrap(email, password, true, true);
              verify(mvp2FixtureService).bootstrap();
              verify(commerceDemoFixtureService).bootstrap();
            });
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void customerCatalogModeControlsFoundationFixtureVisibility(boolean customerCatalogV3Enabled) {
    String email = runtimeQaEmail();
    String password = UUID.randomUUID().toString();
    contextRunner
        .withPropertyValues(
            "spring.profiles.active=local-integration",
            "pawcycle.local-qa-bootstrap.enabled=true",
            "pawcycle.local-qa-bootstrap.email=" + email,
            "pawcycle.local-qa-bootstrap.password=" + password,
            "pawcycle.local-customer-catalog-v3.enabled=" + customerCatalogV3Enabled)
        .run(
            context -> {
              context.getBean("localQaBootstrapRunner", ApplicationRunner.class).run(null);
              verify(bootstrapService).bootstrap(email, password, false, !customerCatalogV3Enabled);
              verify(mvp2FixtureService).bootstrap();
            });
  }

  @Test
  void runnerPropagatesBootstrapFailureAndDoesNotCreateMvp2Fixture() {
    String email = runtimeQaEmail();
    String password = UUID.randomUUID().toString();
    doThrow(new LocalQaBootstrapException("로컬 QA bootstrap 설정 오류"))
        .when(bootstrapService)
        .bootstrap(email, password, false, true);

    contextRunner
        .withPropertyValues(
            "spring.profiles.active=local-integration",
            "pawcycle.local-qa-bootstrap.enabled=true",
            "pawcycle.local-qa-bootstrap.email=" + email,
            "pawcycle.local-qa-bootstrap.password=" + password)
        .run(
            context -> {
              ApplicationRunner runner =
                  context.getBean("localQaBootstrapRunner", ApplicationRunner.class);
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
        .when(mvp2FixtureService)
        .bootstrap();

    contextRunner
        .withPropertyValues(
            "spring.profiles.active=local-integration",
            "pawcycle.local-qa-bootstrap.enabled=true",
            "pawcycle.local-qa-bootstrap.email=" + email,
            "pawcycle.local-qa-bootstrap.password=" + password)
        .run(
            context -> {
              ApplicationRunner runner =
                  context.getBean("localQaBootstrapRunner", ApplicationRunner.class);
              assertThatThrownBy(() -> runner.run(null))
                  .isInstanceOf(LocalQaBootstrapException.class);
              verify(bootstrapService).bootstrap(email, password, false, true);
            });
  }

  @Test
  void runnerPropagatesDemoFixtureFailureAfterExistingFixtures() {
    String email = runtimeQaEmail();
    String password = UUID.randomUUID().toString();
    doThrow(new LocalQaBootstrapException("Commerce Demo fixture 설정 오류"))
        .when(commerceDemoFixtureService)
        .bootstrap();

    contextRunner
        .withPropertyValues(
            "spring.profiles.active=local-integration",
            "pawcycle.local-qa-bootstrap.enabled=true",
            "pawcycle.local-qa-bootstrap.email=" + email,
            "pawcycle.local-qa-bootstrap.password=" + password)
        .run(
            context -> {
              ApplicationRunner runner =
                  context.getBean("localQaBootstrapRunner", ApplicationRunner.class);
              runner.run(null);
              ApplicationRunner demoRunner =
                  context.getBean("localDemoCatalogBootstrapRunner", ApplicationRunner.class);
              assertThatThrownBy(() -> demoRunner.run(null))
                  .isInstanceOf(LocalQaBootstrapException.class);
              verify(bootstrapService).bootstrap(email, password, false, true);
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

  @Configuration(proxyBeanMethods = false)
  @Import(DemoProductDetailSectionFixtureService.class)
  static class DetailFixtureConfiguration {}

  @Configuration(proxyBeanMethods = false)
  @Import(LocalCustomerCatalogV3FixtureService.class)
  static class CustomerV3FixtureConfiguration {}
}
