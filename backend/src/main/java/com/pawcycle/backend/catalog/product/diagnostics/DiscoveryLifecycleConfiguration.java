package com.pawcycle.backend.catalog.product.diagnostics;

import org.hibernate.cfg.SessionEventSettings;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.hibernate.autoconfigure.HibernatePropertiesCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.ConfigurableTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "pawcycle.catalog.product.discovery.diagnostics.enabled", havingValue = "true")
public class DiscoveryLifecycleConfiguration {
  @Bean
  HibernatePropertiesCustomizer discoverySessionEvents() {
    return properties -> {
      if (properties.putIfAbsent(SessionEventSettings.AUTO_SESSION_EVENTS_LISTENER,
          DiscoverySessionEventListener.class.getName()) != null) {
        throw new IllegalStateException("Discovery diagnostics cannot replace an existing session listener");
      }
    };
  }

  @Bean
  org.springframework.beans.factory.SmartInitializingSingleton attachDiscoveryTransactionListener(
      PlatformTransactionManager manager, DiscoveryLifecycleDiagnostics diagnostics) {
    return () -> {
      if (!(manager instanceof ConfigurableTransactionManager configurable)) {
        throw new IllegalStateException("Discovery diagnostics require a configurable transaction manager");
      }
      configurable.addListener(diagnostics.transactionListener());
    };
  }
}
