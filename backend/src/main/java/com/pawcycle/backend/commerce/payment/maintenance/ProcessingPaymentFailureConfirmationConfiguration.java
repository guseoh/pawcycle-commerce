package com.pawcycle.backend.commerce.payment.maintenance;

import com.pawcycle.backend.commerce.payment.application.ProcessingPaymentFailureConfirmationService;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnNotWebApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

/** Registers the #417 Phase 2b failure confirmation command only for an explicitly enabled non-web process. */
@Configuration(proxyBeanMethods = false)
@ConditionalOnNotWebApplication
@ConditionalOnProperty(
    prefix = "pawcycle.maintenance.confirm-processing-payment-failure",
    name = "enabled",
    havingValue = "true")
public class ProcessingPaymentFailureConfirmationConfiguration {

  @Bean
  ApplicationRunner processingPaymentFailureConfirmationRunner(
      ProcessingPaymentFailureConfirmationService confirmation,
      ConfigurableApplicationContext context,
      Environment environment) {
    return new ProcessingPaymentFailureConfirmationCommand(
        confirmation,
        context,
        environment,
        System.out,
        code -> System.exit(SpringApplication.exit(context, () -> code)));
  }
}
