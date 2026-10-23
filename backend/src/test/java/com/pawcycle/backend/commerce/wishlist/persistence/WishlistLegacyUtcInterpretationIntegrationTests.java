package com.pawcycle.backend.commerce.wishlist.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.pawcycle.backend.commerce.wishlist.api.WishlistResponse;
import com.pawcycle.backend.commerce.wishlist.application.WishlistApplicationService;
import com.pawcycle.backend.commerce.wishlist.domain.WishlistItemId;
import com.pawcycle.backend.commerce.wishlist.model.WishlistItemView;
import com.pawcycle.backend.member.application.AuthenticatedMemberPrincipal;
import com.pawcycle.backend.member.domain.MemberRole;
import com.pawcycle.backend.support.SecondaryReadFixtures;
import com.pawcycle.timeexperiment.WishlistLegacyBindingProbe;
import jakarta.persistence.EntityManager;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.assertj.core.api.SoftAssertions;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.context.WebApplicationContext;
import tools.jackson.databind.ObjectMapper;

/** Same persisted rows through the frozen old mapping and actual Runtime HTTP. No service/repository substitutes. */
@SpringBootTest(properties = "spring.jpa.open-in-view=false")
@ActiveProfiles("test")
@Import(WishlistLegacyUtcInterpretationIntegrationTests.LegacyFixture.class)
class WishlistLegacyUtcInterpretationIntegrationTests {
  private static final Instant NOW = Instant.parse("2026-10-21T07:00:00.123456Z");
  @Autowired EntityManager entities;
  @Autowired JdbcTemplate jdbc;
  @Autowired PlatformTransactionManager manager;
  @Autowired WishlistApplicationService wishlist;
  @Autowired WebApplicationContext web;
  @Autowired ObjectMapper json;
  private TransactionTemplate tx;
  private long member, category, brand;
  private final Map<Long, String> kinds = new LinkedHashMap<>();

  @BeforeEach
  void committedHistoricRows() {
    tx = new TransactionTemplate(manager);
    tx.executeWithoutResult(status -> {
      var fixtures = new SecondaryReadFixtures(jdbc, entities);
      member = fixtures.member();
      var categoryRow = fixtures.category(true); var brandRow = fixtures.brand(true);
      category = categoryRow.getId(); brand = brandRow.getId();
      for (String kind : List.of("legacy-jdbc-timestamp", "post-278-jpa", "literal-utc", "literal-seoul")) {
        long product = fixtures.product(categoryRow, brandRow, "DOG", "PUBLIC").getId();
        kinds.put(product, kind);
        switch (kind) {
          case "legacy-jdbc-timestamp" -> jdbc.update(
              "INSERT INTO wishlist_items(member_id,product_id,created_at) VALUES (?,?,?)", member, product, Timestamp.from(NOW));
          case "post-278-jpa" -> entities.persist(new WishlistLegacyBindingProbe(new WishlistItemId(member, product),
              LocalDateTime.ofInstant(NOW, ZoneId.of("Asia/Seoul"))));
          default -> jdbc.update("INSERT INTO wishlist_items(member_id,product_id,created_at) VALUES (?,?,cast(? as datetime(6)))",
              member, product, kind.equals("literal-utc") ? "2026-10-21 07:00:00.123456" : "2026-10-21 16:00:00.123456");
        }
      }
      entities.flush(); entities.clear();
    });
  }

  @Test
  void existingRowsAreReinterpretedAsUtcWithoutRewritingStoredValues() throws Exception {
    var before = rawValues();
    List<WishlistItemView> legacy = tx.execute(status -> entities.createQuery("""
        select new com.pawcycle.backend.commerce.wishlist.persistence.WishlistItemRow(item.id.productId, product.name, item.createdAt)
        from WishlistLegacyBindingProbe item join Product product on product.id=item.id.productId
        where item.id.memberId=:member order by item.createdAt desc, item.id.productId desc
        """, WishlistItemRow.class).setParameter("member", member).getResultList().stream().map(WishlistItemRow::toView).toList());
    var actual = wishlist.list(member);
    var auth = new UsernamePasswordAuthenticationToken(new AuthenticatedMemberPrincipal(member, MemberRole.USER),
        null, List.of(new SimpleGrantedAuthority("ROLE_USER")));
    String body = MockMvcBuilders.webAppContextSetup(web).apply(springSecurity()).build()
        .perform(get("/api/wishlist").with(authentication(auth))).andExpect(status().isOk())
        .andReturn().getResponse().getContentAsString();
    var rows = new ArrayList<Map<String, Object>>();
    var checks = new SoftAssertions();
    for (var entry : kinds.entrySet()) {
      var old = legacy.stream().filter(row -> row.productId() == entry.getKey()).findFirst().orElseThrow();
      var current = actual.stream().filter(row -> row.productId() == entry.getKey()).findFirst().orElseThrow();
      rows.add(Map.of("writer", entry.getValue(), "raw", before.get(entry.getKey()),
          "legacyDto", old.createdAt().toInstant().toString(), "actualDto", current.createdAt().toInstant().toString(),
          "legacyHttp", json.readTree(json.writeValueAsString(old.createdAt())),
          "actualHttp", json.readTree(json.writeValueAsString(current.createdAt()))));
      // Policy #448 explicitly accepts reinterpreting every stored DATETIME as UTC.
      checks.assertThat(current.createdAt().toInstant()).as("raw UTC interpretation: %s", entry.getValue())
          .isEqualTo(LocalDateTime.parse(before.get(entry.getKey())).toInstant(ZoneOffset.UTC));
      int oldMappingOffset = ZoneId.systemDefault().getRules().getOffset(NOW).getTotalSeconds();
      checks.assertThat(old.createdAt().toInstant()).as("accepted old/new mapping delta: %s", entry.getValue())
          .isEqualTo(current.createdAt().toInstant().plusSeconds(oldMappingOffset));
    }
    checks.assertThat(actual.stream().map(WishlistItemView::productId).toList())
        .isEqualTo(legacy.stream().map(WishlistItemView::productId).toList());
    checks.assertThat(json.readTree(body)).as("actual Runtime HTTP uses approved UTC interpretation")
        .isEqualTo(json.readTree(json.writeValueAsString(WishlistResponse.from(actual))));
    checks.assertThat(rawValues()).as("reads must not rewrite stored timestamps").isEqualTo(before);
    System.out.println("LEGACY_C2_OBSERVATION " + json.writeValueAsString(Map.of(
        "jvmZone", ZoneId.systemDefault().getId(), "rows", rows, "rawUnchanged", rawValues().equals(before))));
    checks.assertAll();
  }

  private Map<Long, String> rawValues() {
    var result = new LinkedHashMap<Long, String>();
    for (long product : kinds.keySet()) result.put(product, jdbc.queryForObject(
        "SELECT DATE_FORMAT(created_at,'%Y-%m-%dT%H:%i:%s.%f') FROM wishlist_items WHERE member_id=? AND product_id=?",
        String.class, member, product));
    return result;
  }

  @AfterEach
  void cleanup() {
    if (tx == null) return;
    tx.executeWithoutResult(status -> {
      jdbc.update("DELETE FROM wishlist_items WHERE member_id=?", member);
      for (long product : kinds.keySet()) jdbc.update("DELETE FROM products WHERE id=?", product);
      jdbc.update("DELETE FROM brands WHERE id=?", brand); jdbc.update("DELETE FROM categories WHERE id=?", category);
      jdbc.update("DELETE FROM members WHERE id=?", member);
    });
  }

  @TestConfiguration(proxyBeanMethods = false)
  @EntityScan(basePackages = {"com.pawcycle.backend", "com.pawcycle.timeexperiment"})
  static class LegacyFixture {
    @Bean @Primary Clock fixedLegacyClock() { return Clock.fixed(NOW, ZoneId.of("Asia/Seoul")); }
  }
}
