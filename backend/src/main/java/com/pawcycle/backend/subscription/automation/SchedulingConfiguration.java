package com.pawcycle.backend.subscription.automation;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

/**
 * Background scheduling for the application process. A one-shot maintenance process that runs the
 * #417 payment recovery command must not run any scheduled job, so scheduling is off there.
 */
@Configuration
@EnableScheduling
@ConditionalOnProperty(
    prefix = "pawcycle.maintenance.recover-processing-payment",
    name = "enabled",
    havingValue = "false",
    matchIfMissing = true)
class SchedulingConfiguration {
  @Bean(name = "idempotencyMetricsTaskScheduler", defaultCandidate = false)
  ThreadPoolTaskScheduler idempotencyMetricsTaskScheduler() {
    ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
    scheduler.setPoolSize(1);
    scheduler.setThreadNamePrefix("idempotency-metrics-");
    return scheduler;
  }
}
