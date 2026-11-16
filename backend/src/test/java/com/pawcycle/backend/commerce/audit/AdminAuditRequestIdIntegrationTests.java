package com.pawcycle.backend.commerce.audit;

import static org.assertj.core.api.Assertions.assertThat;

import com.pawcycle.backend.commerce.audit.persistence.AdminAuditPersistenceAdapter;
import com.pawcycle.backend.common.web.RequestCorrelationFilter;
import com.pawcycle.backend.support.SecondaryReadFixtures;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

/** The admin audit row keeps the id of the request that caused it (column existed since V17). */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class AdminAuditRequestIdIntegrationTests {
  @Autowired private AdminAuditPersistenceAdapter audits;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private EntityManager entities;

  @AfterEach
  void clearMdc() {
    MDC.clear();
  }

  @Test
  void auditRowStoresTheCurrentRequestId() {
    long admin = new SecondaryReadFixtures(jdbc, entities).member();
    MDC.put(RequestCorrelationFilter.REQUEST_ID_MDC_KEY, "audit-request-1");

    audits.append(admin, "OBS_LOG_TEST", "PAYMENT", 1L);

    assertThat(requestIdOfLatestAuditBy(admin)).isEqualTo("audit-request-1");
  }

  @Test
  void auditOutsideARequestHasNoRequestId() {
    long admin = new SecondaryReadFixtures(jdbc, entities).member();

    audits.append(admin, "OBS_LOG_TEST", "PAYMENT", 1L);

    assertThat(requestIdOfLatestAuditBy(admin)).isNull();
  }

  private String requestIdOfLatestAuditBy(long admin) {
    return jdbc.queryForObject(
        "SELECT request_id FROM admin_audit_logs WHERE admin_id=? ORDER BY id DESC LIMIT 1",
        String.class, admin);
  }
}
