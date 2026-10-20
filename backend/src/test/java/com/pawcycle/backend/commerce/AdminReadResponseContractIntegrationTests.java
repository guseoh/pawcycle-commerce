package com.pawcycle.backend.commerce;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.pawcycle.backend.commerce.audit.application.AdminAuditEntry;
import com.pawcycle.backend.commerce.audit.application.AdminAuditService;
import com.pawcycle.backend.commerce.inventory.application.InventoryAdminApplicationService;
import com.pawcycle.backend.commerce.inventory.application.InventoryAdminView;
import com.pawcycle.backend.commerce.operations.application.OperationsPendingView;
import com.pawcycle.backend.commerce.operations.application.OperationsQueryService;
import com.pawcycle.backend.member.application.AuthenticatedMemberPrincipal;
import com.pawcycle.backend.member.domain.MemberRole;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** R2-01: protect HTTP payloads independently of the application read projections. */
@SpringBootTest
@ActiveProfiles("test")
class AdminReadResponseContractIntegrationTests {
  @Autowired private WebApplicationContext context;
  @Autowired private ObjectMapper json;
  @MockitoBean private InventoryAdminApplicationService inventories;
  @MockitoBean private AdminAuditService audits;
  @MockitoBean private OperationsQueryService operations;

  private MockMvc mvc;

  @BeforeEach
  void setUp() {
    mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
  }

  @Test
  void inventoryGetPreservesAllWireFieldsAndApplicationOrdering() throws Exception {
    when(inventories.list()).thenReturn(List.of(
        new InventoryAdminView(22L, 20, 4, 9L, "SKU-22"),
        new InventoryAdminView(11L, 0, 3, 10L, "SKU-11")));

    JsonNode rows = adminGet("/api/admin/inventories");
    assertThat(rows.isArray()).isTrue();
    assertThat(rows.size()).isEqualTo(2);

    JsonNode first = rows.get(0);
    assertThat(first.size()).isEqualTo(5);
    assertThat(first.get("skuId").asLong()).isEqualTo(22L);
    assertThat(first.get("availableQuantity").asInt()).isEqualTo(20);
    assertThat(first.get("reservedQuantity").asInt()).isEqualTo(4);
    assertThat(first.get("version").asLong()).isEqualTo(9L);
    assertThat(first.get("skuCode").asText()).isEqualTo("SKU-22");

    JsonNode second = rows.get(1);
    assertThat(second.size()).isEqualTo(5);
    assertThat(second.get("skuId").asLong()).isEqualTo(11L);
    assertThat(second.get("availableQuantity").asInt()).isZero();
    assertThat(second.get("reservedQuantity").asInt()).isEqualTo(3);
    assertThat(second.get("version").asLong()).isEqualTo(10L);
    assertThat(second.get("skuCode").asText()).isEqualTo("SKU-11");
    verify(inventories).list();
  }

  @Test
  void auditGetPreservesFieldsTimestampAndApplicationOrdering() throws Exception {
    Timestamp newest = Timestamp.from(Instant.parse("2026-10-20T08:00:00Z"));
    Timestamp older = Timestamp.from(Instant.parse("2026-10-19T08:00:00Z"));
    when(audits.list()).thenReturn(List.of(
        new AdminAuditEntry(72L, 8L, "INVENTORY_ADJUST", "SKU", 44L, newest),
        new AdminAuditEntry(71L, 9L, "STATUS_CHANGE", "ORDER", 43L, older)));

    JsonNode rows = adminGet("/api/admin/audit-logs");
    assertThat(rows.isArray()).isTrue();
    assertThat(rows.size()).isEqualTo(2);

    JsonNode first = rows.get(0);
    assertThat(first.size()).isEqualTo(6);
    assertThat(first.get("auditLogId").asLong()).isEqualTo(72L);
    assertThat(first.get("adminId").asLong()).isEqualTo(8L);
    assertThat(first.get("action").asText()).isEqualTo("INVENTORY_ADJUST");
    assertThat(first.get("targetType").asText()).isEqualTo("SKU");
    assertThat(first.get("targetId").asLong()).isEqualTo(44L);
    assertThat(first.get("createdAt")).isEqualTo(json.readTree(json.writeValueAsString(newest)));

    JsonNode second = rows.get(1);
    assertThat(second.size()).isEqualTo(6);
    assertThat(second.get("auditLogId").asLong()).isEqualTo(71L);
    assertThat(second.get("adminId").asLong()).isEqualTo(9L);
    assertThat(second.get("action").asText()).isEqualTo("STATUS_CHANGE");
    assertThat(second.get("targetType").asText()).isEqualTo("ORDER");
    assertThat(second.get("targetId").asLong()).isEqualTo(43L);
    assertThat(second.get("createdAt")).isEqualTo(json.readTree(json.writeValueAsString(older)));
    verify(audits).list();
  }

  @Test
  void operationsGetPreservesActionsNullAttemptAndApplicationOrdering() throws Exception {
    Timestamp created = Timestamp.from(Instant.parse("2026-10-20T08:00:00Z"));
    when(operations.pending()).thenReturn(List.of(
        new OperationsPendingView(
            "PAYMENT_PROCESSING", 90L, created, null, List.of("RECONCILE_PAYMENT")),
        new OperationsPendingView(
            "REFUND_PROCESSING", 91L, created, 2,
            List.of("RECONCILE_REFUND", "RETRY_REFUND"))));

    JsonNode rows = adminGet("/api/admin/operations");
    assertThat(rows.isArray()).isTrue();
    assertThat(rows.size()).isEqualTo(2);

    JsonNode first = rows.get(0);
    assertThat(first.size()).isEqualTo(5);
    assertThat(first.get("type").asText()).isEqualTo("PAYMENT_PROCESSING");
    assertThat(first.get("referenceId").asLong()).isEqualTo(90L);
    assertThat(first.get("createdAt")).isEqualTo(json.readTree(json.writeValueAsString(created)));
    assertThat(first.has("attemptNo")).isTrue();
    assertThat(first.get("attemptNo").isNull()).isTrue();
    assertThat(first.get("availableActions").size()).isEqualTo(1);
    assertThat(first.get("availableActions").get(0).asText()).isEqualTo("RECONCILE_PAYMENT");

    JsonNode second = rows.get(1);
    assertThat(second.size()).isEqualTo(5);
    assertThat(second.get("type").asText()).isEqualTo("REFUND_PROCESSING");
    assertThat(second.get("referenceId").asLong()).isEqualTo(91L);
    assertThat(second.get("createdAt")).isEqualTo(json.readTree(json.writeValueAsString(created)));
    assertThat(second.get("attemptNo").asInt()).isEqualTo(2);
    assertThat(second.get("availableActions").size()).isEqualTo(2);
    assertThat(second.get("availableActions").get(0).asText()).isEqualTo("RECONCILE_REFUND");
    assertThat(second.get("availableActions").get(1).asText()).isEqualTo("RETRY_REFUND");
    verify(operations).pending();
  }

  private JsonNode adminGet(String url) throws Exception {
    var admin = new UsernamePasswordAuthenticationToken(
        new AuthenticatedMemberPrincipal(17L, MemberRole.ADMIN),
        null,
        List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
    String payload = mvc.perform(get(url).with(authentication(admin)))
        .andExpect(status().isOk())
        .andReturn().getResponse().getContentAsString();
    return json.readTree(payload);
  }
}
