package com.pawcycle.backend.subscription.v2;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

@Configuration
@EnableScheduling
class V2SchedulingConfiguration {
	@Bean(name = "idempotencyMetricsTaskScheduler")
	ThreadPoolTaskScheduler idempotencyMetricsTaskScheduler() {
		ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
		scheduler.setPoolSize(1);
		scheduler.setThreadNamePrefix("idempotency-metrics-");
		return scheduler;
	}
}
