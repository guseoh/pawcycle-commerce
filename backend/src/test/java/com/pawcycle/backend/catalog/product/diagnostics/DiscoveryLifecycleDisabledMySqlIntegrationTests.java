package com.pawcycle.backend.catalog.product.diagnostics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doAnswer;

import com.pawcycle.backend.catalog.product.application.ProductQueryService;
import com.pawcycle.backend.catalog.product.persistence.ProductDiscoveryQueryRepository;
import com.zaxxer.hikari.HikariDataSource;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.sql.Connection;
import java.util.concurrent.atomic.AtomicReference;
import org.hibernate.Session;
import jakarta.persistence.EntityManagerFactory;
import io.micrometer.core.instrument.MeterRegistry;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.ConfigurableTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;

@SpringBootTest(properties = "pawcycle.catalog.product.discovery.diagnostics.enabled=false")
@ActiveProfiles("test")
class DiscoveryLifecycleDisabledMySqlIntegrationTests {
  @Autowired ProductQueryService service;
  @MockitoSpyBean ProductDiscoveryQueryRepository discoveryQueries;
  @PersistenceContext EntityManager entityManager;

  @Autowired MeterRegistry registry;
  @Autowired DiscoveryLifecycleDiagnostics diagnostics;
  @Autowired PlatformTransactionManager manager;
  @Autowired DataSource dataSource;
  @Autowired EntityManagerFactory entityManagerFactory;

  @Test void disabledDiscoveryHasNoListenerMetersOrContextAndReturnsConnection() {
    assertThat(dataSource).isInstanceOf(HikariDataSource.class);
    AtomicReference<Connection> captured = new AtomicReference<>();
    // Spy only this query boundary. The service, JPA transaction and actual SQL remain real.
    // ProductDiscoveryReader opens the transaction before invoking this repository.
    doAnswer(invocation -> {
      entityManager.unwrap(Session.class).doWork(connection -> {
        assertThat(connection.isClosed()).as("request JDBC lease is open during the query").isFalse();
        captured.set(connection);
      });
      return invocation.callRealMethod();
    }).when(discoveryQueries).read(
        any(), any(), any(), any(), any(), any(), any(), any(), any(), any(),
        anyInt(), anyInt(), any());

    int activeBefore = DiscoveryPoolCiEvidence.active(dataSource);
    assertThat(service.findProducts().items()).isNotNull();
    assertThat(captured.get()).as("real Discovery query captured its JDBC lease").isNotNull();
    assertThat(captured.get().isClosed()).as("Discovery JDBC lease returned after service call").isTrue();
    assertThat(registry.getMeters()).noneMatch(m -> m.getId().getName().startsWith(DiscoveryLifecycleDiagnostics.METRIC));
    assertThat(((ConfigurableTransactionManager) manager).getTransactionExecutionListeners()).doesNotContain(diagnostics.transactionListener());
    assertThat(DiscoveryLifecycleDiagnostics.currentScope()).isNull();
    DiscoveryPoolCiEvidence.assertRequestThreadClean(entityManagerFactory, dataSource, "disabledDiscovery");
    DiscoveryPoolCiEvidence.observeSharedPool(dataSource, activeBefore, "disabledDiscovery");
  }
}
