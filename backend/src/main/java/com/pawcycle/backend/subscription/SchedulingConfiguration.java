package com.pawcycle.backend.subscription;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

@Configuration
@EnableScheduling
class SchedulingConfiguration {
  @Bean(name = "idempotencyMetricsTaskScheduler", defaultCandidate = false)
  ThreadPoolTaskScheduler idempotencyMetricsTaskScheduler() {
    ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
    scheduler.setPoolSize(1);
    scheduler.setThreadNamePrefix("idempotency-metrics-");
    return scheduler;
  }
}
