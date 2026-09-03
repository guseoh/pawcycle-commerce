package com.pawcycle.backend.member.maintenance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.pawcycle.backend.member.application.EmailNormalizer;
import com.pawcycle.backend.member.persistence.MemberRepository;
import org.junit.jupiter.api.Test;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.security.crypto.password.PasswordEncoder;

class ProductionAuthSmokeMemberMaintenanceConfigurationTests {

  private final ApplicationContextRunner nonWebContextRunner =
      new ApplicationContextRunner()
          .withUserConfiguration(ProductionAuthSmokeMemberMaintenanceConfiguration.class)
          .withBean(EmailNormalizer.class, EmailNormalizer::new)
          .withBean(PasswordEncoder.class, () -> mock(PasswordEncoder.class))
          .withBean(MemberRepository.class, () -> mock(MemberRepository.class));

  @Test
  void maintenanceBeansAreAbsentWithoutExplicitEnablement() {
    nonWebContextRunner.run(
        context -> {
          assertThat(context).doesNotHaveBean(ProductionAuthSmokeMemberService.class);
          assertThat(context).doesNotHaveBean("productionAuthSmokeMemberRunner");
        });
  }

  @Test
  void enabledNonWebMaintenanceModeCreatesOneShotRunner() {
    nonWebContextRunner
        .withPropertyValues("pawcycle.maintenance.create-auth-smoke-member.enabled=true")
        .run(
            context -> {
              assertThat(context).hasSingleBean(ProductionAuthSmokeMemberService.class);
              assertThat(context.getBean("productionAuthSmokeMemberRunner"))
                  .isInstanceOf(ApplicationRunner.class);
            });
  }

  @Test
  void webApplicationBlocksRunnerEvenWhenFlagIsEnabled() {
    new WebApplicationContextRunner()
        .withUserConfiguration(ProductionAuthSmokeMemberMaintenanceConfiguration.class)
        .withPropertyValues("pawcycle.maintenance.create-auth-smoke-member.enabled=true")
        .run(
            context -> {
              assertThat(context).doesNotHaveBean(ProductionAuthSmokeMemberService.class);
              assertThat(context).doesNotHaveBean("productionAuthSmokeMemberRunner");
            });
  }
}
