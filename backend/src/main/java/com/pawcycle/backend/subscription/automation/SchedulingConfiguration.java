package com.pawcycle.backend.subscription.automation;

import com.pawcycle.backend.common.scheduling.SchedulerNames;
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
  /** Metric refreshes; see {@link SchedulerNames}. */
  @Bean(name = SchedulerNames.METRICS, defaultCandidate = false)
  ThreadPoolTaskScheduler metricsTaskScheduler() {
    return singleThread("metrics-refresh-");
  }

  /** Jobs that wait on an external provider; see {@link SchedulerNames}. */
  @Bean(name = SchedulerNames.PROVIDER_CALLS, defaultCandidate = false)
  ThreadPoolTaskScheduler providerCallTaskScheduler() {
    return singleThread("provider-call-");
  }

  private static ThreadPoolTaskScheduler singleThread(String prefix) {
    ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
    scheduler.setPoolSize(1);
    scheduler.setThreadNamePrefix(prefix);
    return scheduler;
  }
}
