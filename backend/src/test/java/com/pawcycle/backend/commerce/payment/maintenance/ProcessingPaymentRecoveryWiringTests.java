package com.pawcycle.backend.commerce.payment.maintenance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.pawcycle.backend.commerce.payment.application.ProcessingPaymentRecoveryService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;

/** Entry-point wiring that the service-level tests cannot see (review finding on #532). */
class ProcessingPaymentRecoveryWiringTests {
  @Test
  void runnerExistsOnlyInEnabledNonWebProcess() {
    var base = new ApplicationContextRunner()
        .withUserConfiguration(ProcessingPaymentRecoveryConfiguration.class)
        .withBean(ProcessingPaymentRecoveryService.class, () -> mock(ProcessingPaymentRecoveryService.class));
    base.withPropertyValues("pawcycle.maintenance.recover-processing-payment.enabled=true")
        .run(context -> assertThat(context).hasSingleBean(ApplicationRunner.class));
    base.run(context -> assertThat(context).doesNotHaveBean(ApplicationRunner.class));
    new WebApplicationContextRunner()
        .withUserConfiguration(ProcessingPaymentRecoveryConfiguration.class)
        .withBean(ProcessingPaymentRecoveryService.class, () -> mock(ProcessingPaymentRecoveryService.class))
        .withPropertyValues("pawcycle.maintenance.recover-processing-payment.enabled=true")
        .run(context -> assertThat(context).doesNotHaveBean(ApplicationRunner.class));
  }

  @Test
  void backgroundSchedulingIsOffInTheRecoveryProcess() throws Exception {
    Class<?> scheduling =
        Class.forName("com.pawcycle.backend.subscription.automation.SchedulingConfiguration");
    var runner = new ApplicationContextRunner().withUserConfiguration(scheduling);
    runner.run(context -> assertThat(context).hasSingleBean(scheduling));
    runner.withPropertyValues("pawcycle.maintenance.recover-processing-payment.enabled=true")
        .run(context -> assertThat(context).doesNotHaveBean(scheduling));
  }
}
