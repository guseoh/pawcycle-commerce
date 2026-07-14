package com.pawcycle.backend.subscription.application;

import java.time.Clock;
import java.time.ZoneId;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class SubscriptionClockConfiguration {

	@Bean
	Clock subscriptionClock() {
		return Clock.system(ZoneId.of("Asia/Seoul"));
	}
}
