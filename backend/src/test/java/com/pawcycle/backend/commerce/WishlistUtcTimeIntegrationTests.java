package com.pawcycle.backend.commerce;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.pawcycle.backend.commerce.wishlist.api.WishlistResponse;
import com.pawcycle.backend.commerce.wishlist.application.WishlistApplicationService;
import com.pawcycle.backend.member.application.AuthenticatedMemberPrincipal;
import com.pawcycle.backend.member.domain.MemberRole;
import com.pawcycle.backend.support.SecondaryReadFixtures;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.List;
import org.assertj.core.api.SoftAssertions;
import org.hibernate.engine.spi.SessionFactoryImplementor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
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
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.context.WebApplicationContext;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest(properties = {"spring.jpa.open-in-view=false", "spring.jpa.properties.hibernate.generate_statistics=true"})
@ActiveProfiles("test")
@Import(WishlistUtcTimeIntegrationTests.FixedClock.class)
class WishlistUtcTimeIntegrationTests {
  private static final Instant NOW = Instant.parse("2026-10-21T07:00:00.123456Z");
  @Autowired WishlistApplicationService wishlist;
  @Autowired JdbcTemplate jdbc;
  @Autowired EntityManager entities;
  @Autowired EntityManagerFactory emf;
  @Autowired PlatformTransactionManager transactions;
  @Autowired ObjectMapper json;
  @Autowired WebApplicationContext web;
  @Autowired Clock clock;
  private long memberId, productId, categoryId, brandId;

  @Test
  void realAddCommitAndIndependentReadsPreserveClockInstant() throws Exception {
    var factory = emf.unwrap(SessionFactoryImplementor.class);
    var statistics = factory.getStatistics();
    var metadata = new TransactionTemplate(transactions).execute(tx -> {
      var fixtures = new SecondaryReadFixtures(jdbc, entities);
      memberId = fixtures.member();
      var category = fixtures.category(true);
      var brand = fixtures.brand(true);
      categoryId = category.getId();
      brandId = brand.getId();
      productId = fixtures.product(category, brand, "DOG", "PUBLIC").getId();
      wishlist.add(memberId, productId);
      entities.flush();
      return jdbc.queryForMap("SELECT VERSION() AS dbVersion, @@session.time_zone AS sessionZone, @@system_time_zone AS systemZone");
    });
    assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
    assertThat(TransactionSynchronizationManager.hasResource(emf)).isFalse();

    String rawCreatedAt = jdbc.queryForObject(
        "SELECT DATE_FORMAT(created_at,'%Y-%m-%dT%H:%i:%s.%f') FROM wishlist_items WHERE member_id=? AND product_id=?",
        String.class, memberId, productId);
    LocalDateTime jpaCreatedAt = new TransactionTemplate(transactions).execute(tx -> entities.createQuery(
            "select item.createdAt from WishlistItemEntity item where item.id.memberId=:member and item.id.productId=:product",
            LocalDateTime.class).setParameter("member", memberId).setParameter("product", productId).getSingleResult());

    statistics.clear();
    var views = wishlist.list(memberId);
    long readSql = statistics.getPrepareStatementCount();
    long readEntityLoads = statistics.getEntityLoadCount();
    var response = WishlistResponse.from(views);
    var detachedJson = json.writeValueAsString(response);
    long detachedAdditionalSql = statistics.getPrepareStatementCount() - readSql;
    Instant dtoInstant = response.items().getFirst().createdAt().toInstant();
    assertThat(TransactionSynchronizationManager.hasResource(emf)).isFalse();

    var http = MockMvcBuilders.webAppContextSetup(web).apply(springSecurity()).build();
    var principal = new AuthenticatedMemberPrincipal(memberId, MemberRole.USER);
    var body = http.perform(get("/api/wishlist").with(authentication(new UsernamePasswordAuthenticationToken(
            principal, null, List.of(new SimpleGrantedAuthority("ROLE_USER"))))))
        .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
    var wire = json.readTree(body).get("items").get(0).get("createdAt");
    // Use the existing Timestamp serializer for the expected wire value; do not change its format/precision.
    var expectedWire = json.readTree(json.writeValueAsString(Timestamp.from(NOW)));

    var observation = new LinkedHashMap<String, Object>();
    observation.put("jvmZone", ZoneId.systemDefault().getId());
    observation.put("hibernateZone", factory.getSessionFactoryOptions().getJdbcTimeZone().getID());
    observation.put("hibernateVersion", org.hibernate.Version.getVersionString());
    observation.put("database", metadata);
    observation.put("clockZone", clock.getZone().getId());
    observation.put("clockInstant", clock.instant().toString());
    observation.put("clockLocalDateTime", LocalDateTime.now(clock).toString());
    observation.put("dbCreatedAt", rawCreatedAt);
    observation.put("jpaCreatedAt", jpaCreatedAt.toString());
    observation.put("dtoInstant", dtoInstant.toString());
    observation.put("dtoDelta", Duration.between(NOW, dtoInstant).toString());
    observation.put("httpCreatedAt", wire);
    observation.put("expectedClockWire", expectedWire);
    observation.put("readSql", readSql);
    observation.put("readEntityLoads", readEntityLoads);
    observation.put("detachedAdditionalSql", detachedAdditionalSql);
    System.out.println("WISHLIST_TIME_OBSERVATION " + json.writeValueAsString(observation));

    SoftAssertions.assertSoftly(check -> {
      check.assertThat(clock.getZone()).isEqualTo(ZoneId.of("Asia/Seoul"));
      check.assertThat(clock.instant()).isEqualTo(NOW);
      check.assertThat(factory.getSessionFactoryOptions().getJdbcTimeZone().getID()).isEqualTo("UTC");
      check.assertThat(rawCreatedAt).isEqualTo("2026-10-21T07:00:00.123456");
      check.assertThat(jpaCreatedAt).isEqualTo(LocalDateTime.ofInstant(NOW, java.time.ZoneOffset.UTC));
      check.assertThat(jpaCreatedAt.getNano()).isEqualTo(123456000);
      check.assertThat(response.items().getFirst().createdAt().getNanos()).isEqualTo(123456000);
      check.assertThat(readSql).isEqualTo(1);
      check.assertThat(readEntityLoads).isZero();
      check.assertThat(detachedAdditionalSql).isZero();
      check.assertThat(body).isEqualTo(detachedJson);
      check.assertThat(dtoInstant).as("real add -> DTO must preserve the injected Clock instant").isEqualTo(NOW);
      check.assertThat(wire).as("HTTP must preserve the Clock instant through the existing Timestamp format").isEqualTo(expectedWire);
    });
  }

  @AfterEach
  void cleanUp() {
    new TransactionTemplate(transactions).executeWithoutResult(tx -> {
      jdbc.update("DELETE FROM wishlist_items WHERE member_id=?", memberId);
      jdbc.update("DELETE FROM products WHERE id=?", productId);
      jdbc.update("DELETE FROM brands WHERE id=?", brandId);
      jdbc.update("DELETE FROM categories WHERE id=?", categoryId);
      jdbc.update("DELETE FROM members WHERE id=?", memberId);
    });
  }

  @TestConfiguration(proxyBeanMethods = false)
  static class FixedClock {
    @Bean
    @Primary
    Clock wishlistCharacterizationClock() {
      return Clock.fixed(NOW, ZoneId.of("Asia/Seoul"));
    }
  }
}
