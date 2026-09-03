package com.pawcycle.backend.subscription.performance;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.SecurityFilterChain;

@Configuration(proxyBeanMethods = false)
@Profile("subscription-burst-measurement & !production & !prod")
class SubscriptionBurstMeasurementSecurityConfiguration {

  @Bean
  @Order(1)
  SecurityFilterChain subscriptionBurstMeasurementSecurityFilterChain(HttpSecurity http)
      throws Exception {
    http.securityMatcher("/internal/performance/subscription-burst/**")
        .csrf(AbstractHttpConfigurer::disable)
        .authorizeHttpRequests(authorize -> authorize.anyRequest().permitAll());
    return http.build();
  }
}
