package com.pawcycle.backend.catalog.product.diagnostics;

import static org.assertj.core.api.Assertions.assertThat;

import com.pawcycle.backend.catalog.product.application.ProductQueryService;
import jakarta.persistence.EntityManagerFactory;
import io.micrometer.core.instrument.MeterRegistry;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.ConfigurableTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;

@SpringBootTest(properties = "pawcycle.catalog.product.discovery.diagnostics.enabled=false")
@ActiveProfiles("test")
class DiscoveryLifecycleDisabledMySqlIntegrationTests {
  @Autowired ProductQueryService service;

  @Autowired MeterRegistry registry;
  @Autowired DiscoveryLifecycleDiagnostics diagnostics;
  @Autowired PlatformTransactionManager manager;
  @Autowired DataSource dataSource;
  @Autowired EntityManagerFactory entityManagerFactory;

  @Test void disabledDiscoveryHasNoListenerMetersOrContextAndReturnsConnection() {
    int activeBefore = DiscoveryPoolCiEvidence.active(dataSource);
    assertThat(service.findProducts().items()).isNotNull();
    assertThat(registry.getMeters()).noneMatch(m -> m.getId().getName().startsWith(DiscoveryLifecycleDiagnostics.METRIC));
    assertThat(((ConfigurableTransactionManager) manager).getTransactionExecutionListeners()).doesNotContain(diagnostics.transactionListener());
    assertThat(DiscoveryLifecycleDiagnostics.currentScope()).isNull();
    DiscoveryPoolCiEvidence.assertRequestThreadClean(entityManagerFactory, dataSource, "disabledDiscovery");
    DiscoveryPoolCiEvidence.observeSharedPool(dataSource, activeBefore, "disabledDiscovery");
  }
}
