package com.pawcycle.backend.commerce.payment.maintenance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.pawcycle.backend.commerce.payment.application.ProcessingPaymentFailureConfirmationService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;

/** Entry-point wiring of the #417 Phase 2b command, which the service-level tests cannot see. */
class ProcessingPaymentFailureConfirmationWiringTests {
  private static final String ENABLED = "pawcycle.maintenance.confirm-processing-payment-failure.enabled=true";

  @Test
  void runnerExistsOnlyInEnabledNonWebProcess() {
    var base = new ApplicationContextRunner()
        .withUserConfiguration(ProcessingPaymentFailureConfirmationConfiguration.class)
        .withBean(ProcessingPaymentFailureConfirmationService.class,
            () -> mock(ProcessingPaymentFailureConfirmationService.class));
    base.withPropertyValues(ENABLED).run(context -> assertThat(context).hasSingleBean(ApplicationRunner.class));
    base.run(context -> assertThat(context).doesNotHaveBean(ApplicationRunner.class));
    new WebApplicationContextRunner()
        .withUserConfiguration(ProcessingPaymentFailureConfirmationConfiguration.class)
        .withBean(ProcessingPaymentFailureConfirmationService.class,
            () -> mock(ProcessingPaymentFailureConfirmationService.class))
        .withPropertyValues(ENABLED)
        .run(context -> assertThat(context).doesNotHaveBean(ApplicationRunner.class));
  }

  @Test
  void backgroundSchedulingIsOffInTheFailureConfirmationProcess() throws Exception {
    Class<?> scheduling =
        Class.forName("com.pawcycle.backend.subscription.automation.SchedulingConfiguration");
    var runner = new ApplicationContextRunner().withUserConfiguration(scheduling);
    runner.run(context -> assertThat(context).hasSingleBean(scheduling));
    runner.withPropertyValues("pawcycle.maintenance.recover-processing-payment.enabled=false",
            "pawcycle.maintenance.confirm-processing-payment-failure.enabled=false")
        .run(context -> assertThat(context).hasSingleBean(scheduling));
    runner.withPropertyValues(ENABLED).run(context -> assertThat(context).doesNotHaveBean(scheduling));
    runner.withPropertyValues("pawcycle.maintenance.recover-processing-payment.enabled=true")
        .run(context -> assertThat(context).doesNotHaveBean(scheduling));
  }
}
