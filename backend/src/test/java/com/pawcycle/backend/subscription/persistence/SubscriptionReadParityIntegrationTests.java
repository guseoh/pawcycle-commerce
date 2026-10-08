package com.pawcycle.backend.subscription.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.reset;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import com.pawcycle.backend.catalog.category.domain.Category;
import com.pawcycle.backend.catalog.category.persistence.CategoryRepository;
import com.pawcycle.backend.catalog.product.domain.Product;
import com.pawcycle.backend.catalog.product.persistence.ProductRepository;
import com.pawcycle.backend.catalog.sku.persistence.SkuRepository;
import com.pawcycle.backend.member.application.AuthenticatedMemberPrincipal;
import com.pawcycle.backend.member.domain.Member;
import com.pawcycle.backend.member.persistence.MemberRepository;
import com.pawcycle.backend.subscription.application.SubscriptionService;
import jakarta.persistence.EntityManagerFactory;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Modifier;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

/** Same MySQL rows, frozen main JDBC oracle, and the unchanged real HTTP/application stack. */
@SpringBootTest(properties = {
    "spring.datasource.hikari.maximum-pool-size=2",
    "spring.datasource.hikari.minimum-idle=0"
})
@ActiveProfiles("test")
@Transactional
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class SubscriptionReadParityIntegrationTests {
  @Autowired JdbcTemplate jdbc;
  @MockitoSpyBean SubscriptionAggregatePersistence store;
  @Autowired SubscriptionService service;
  @Autowired MemberRepository members;
  @Autowired CategoryRepository categories;
  @Autowired ProductRepository products;
  @Autowired SkuRepository skus;
  @Autowired WebApplicationContext context;
  @Autowired EntityManagerFactory emf;
  @Autowired Clock clock;

  private LegacySubscriptionReadReference legacy;
  private MockMvc http;
  private long memberId, otherMemberId, petId, catId, versionId, skuId, secondSkuId;
  private long subscriptionId, snapshotId, nextId, pendingId, pendingVersionId;
  private LocalDate today;

  @BeforeEach
  void fixture() {
    legacy = new LegacySubscriptionReadReference(jdbc);
    http = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    today = LocalDate.now(clock);
    memberId = members.saveAndFlush(new Member("t08-" + UUID.randomUUID() + "@example.test", "fixture-only")).getId();
    otherMemberId = members.saveAndFlush(new Member("t08-other-" + UUID.randomUUID() + "@example.test", "fixture-only")).getId();
    var category = categories.saveAndFlush(new Category("t08-" + UUID.randomUUID(), "t08", 0, true));
    var product = products.saveAndFlush(new Product(category, "T08 product", "fixture", null, "DOG", null, "PUBLIC"));
    skuId = skus.saveAndFlush(com.pawcycle.backend.support.TestSkuFactory.sku(
        product, "t08-" + UUID.randomUUID(), new BigDecimal("19900.00"), true, 1)).getId();
    secondSkuId = skus.saveAndFlush(com.pawcycle.backend.support.TestSkuFactory.sku(
        product, "t08-" + UUID.randomUUID(), new BigDecimal("1234.56"), true, 2)).getId();
    petId = store.insertPet(memberId, "nullable profile", "DOG");
    catId = store.insertPet(memberId, "CAT", "CAT");
    store.insertPet(otherMemberId, "foreign", "DOG");
    store.updatePet(memberId, catId, null, false, "breed", true, new BigDecimal("3.20"), true);
    versionId = plan("boundary", "DOG", true, false, today, today, 24000L);
    pendingVersionId = plan("nullable dates", "DOG", true, false, null, null, 9007199254740991L);
    plan("migration", "DOG", true, true, null, null, 0);
    plan("future", "DOG", true, false, today.plusDays(1), null, 0);
    plan("expired", "DOG", true, false, null, today.minusDays(1), 0);
    plan("off sale", "DOG", false, false, null, null, 0);
    plan(null, "DOG", true, false, null, null, 0);
    plan("cat", "CAT", true, false, null, null, 0);
    subscriptionId = subscription(petId, "ACTIVE");
    snapshotId = store.findOwnedSubscription(memberId, subscriptionId).currentSnapshotId();
    long processed = store.insertScheduledAndReturnId(subscriptionId, today);
    jdbc.update("""
        INSERT INTO subscription_orders(member_id,subscription_id,schedule_id,effective_snapshot_id,
          source_plan_version_id,scheduled_date,processed_at,package_total_krw,status)
        VALUES (?,?,?,?,?,?,CURRENT_TIMESTAMP(6),24000,'CREATED')
        """, memberId, subscriptionId, processed, snapshotId, versionId, today);
    nextId = store.insertScheduledAndReturnId(subscriptionId, today.plusDays(7));
    store.insertScheduledAndReturnId(subscriptionId, today.plusDays(14));
    pendingId = store.createSnapshot(subscriptionId, pendingVersionId, 8, 9007199254740991L);
    store.replacePendingPlanChange(subscriptionId, pendingId, nextId);
    store.upsertScheduleAddon(nextId, secondSkuId, 3, new BigDecimal("1234.56"));
    // History ties exercise the id DESC tiebreaker; microseconds must survive DATETIME(6).
    for (String command : List.of("PAUSE", "RESUME", "SKIP_NEXT")) {
      jdbc.update("""
          INSERT INTO subscription_command_history(subscription_id,command_type,occurred_at,version_before,version_after)
          VALUES (?,?,'2026-10-07 23:59:59.123456',0,1)
          """, subscriptionId, command);
    }
    subscription(catId, "PAUSED");
    long nullablePet = subscription(petId, "CANCELED");
    jdbc.update("UPDATE subscriptions SET pet_id=NULL WHERE id=?", nullablePet);
    long legacySubscription = subscription(petId, "ACTIVE");
    jdbc.update("UPDATE subscriptions SET runtime_managed=false WHERE id=?", legacySubscription);
  }

  @Test
  void everyConvertedReadMatchesFrozenJdbcIncludingEmptyBatchAndPagination() {
    assertThat(store.findOwnedPet(memberId, petId)).isEqualTo(legacy.findOwnedPet(memberId, petId));
    assertThat(store.findOwnedPet(memberId, catId)).isEqualTo(legacy.findOwnedPet(memberId, catId));
    assertThat(store.findPlanVersion(versionId)).isEqualTo(legacy.findPlanVersion(versionId));
    assertThat(store.findOwnedSubscription(memberId, subscriptionId)).isEqualTo(legacy.findOwnedSubscription(memberId, subscriptionId));
    for (int page : List.of(0, 1, 2, 99)) {
      assertThat(store.findPets(memberId, page, 1)).isEqualTo(legacy.findPets(memberId, page, 1));
      assertThat(store.findSalePlanVersions("DOG", today, page, 1)).isEqualTo(legacy.findSalePlanVersions("DOG", today, page, 1));
      assertThat(store.findSubscriptions(memberId, page, 1)).isEqualTo(legacy.findSubscriptions(memberId, page, 1));
      assertThat(store.findScheduleViews(subscriptionId, page, 1)).isEqualTo(legacy.findScheduleViews(subscriptionId, page, 1));
      assertThat(store.findCommandHistory(subscriptionId, page, 1)).isEqualTo(legacy.findCommandHistory(subscriptionId, page, 1));
    }
    assertThat(store.findPlanItems(versionId)).isEqualTo(legacy.findPlanItems(versionId));
    assertThat(store.findDeliveryCycles(versionId)).isEqualTo(legacy.findDeliveryCycles(versionId));
    assertThat(store.findSnapshot(snapshotId)).isEqualTo(legacy.findSnapshot(snapshotId));
    assertThat(store.findPendingChange(subscriptionId)).isEqualTo(legacy.findPendingChange(subscriptionId));
    assertThat(store.findNextSchedule(subscriptionId, today)).isEqualTo(legacy.findNextSchedule(subscriptionId, today));
    assertThat(store.findNextDeliverySchedule(subscriptionId)).isEqualTo(legacy.findNextDeliverySchedule(subscriptionId));
    assertThat(store.findSnapshotItemDetails(snapshotId)).isEqualTo(legacy.findSnapshotItemDetails(snapshotId));
    assertThat(store.findScheduleAddons(nextId)).isEqualTo(legacy.findScheduleAddons(nextId));
    assertThat(store.scheduleAddonCount(nextId)).isEqualTo(legacy.scheduleAddonCount(nextId));
    for (List<Long> ids : List.of(List.<Long>of(), List.of(versionId, pendingVersionId, Long.MAX_VALUE))) {
      assertThat(store.findPlanItems(ids)).isEqualTo(legacy.findPlanItems(ids));
      assertThat(store.findDeliveryCycles(ids)).isEqualTo(legacy.findDeliveryCycles(ids));
    }
    for (List<Long> ids : List.of(List.<Long>of(), List.of(petId, catId, Long.MAX_VALUE))) {
      assertThat(store.findOwnedPets(memberId, ids)).isEqualTo(legacy.findOwnedPets(memberId, ids));
    }
    for (List<Long> ids : List.of(List.<Long>of(), List.of(snapshotId, pendingId, Long.MAX_VALUE))) {
      assertThat(store.findSnapshots(ids)).isEqualTo(legacy.findSnapshots(ids));
      assertThat(store.findSnapshotItems(ids)).isEqualTo(legacy.findSnapshotItems(ids));
    }
    for (List<Long> ids : List.of(List.<Long>of(), List.of(subscriptionId, Long.MAX_VALUE))) {
      assertThat(store.findNextSchedules(ids, today)).isEqualTo(legacy.findNextSchedules(ids, today));
    }
    assertThat(store.findCommandHistory(subscriptionId, 0, 20).items().getFirst().occurredAt())
        .endsWith(".123456+09:00");
    assertThat(store.findNextSchedule(subscriptionId, today)).contains(today.plusDays(7));
    assertThat(store.findSnapshot(pendingId).packagePriceKrw()).isEqualTo(9007199254740991L);
  }

  @Test
  void httpJsonErrorsEtagPendingAndEffectiveSnapshotPrecedenceMatchJdbc() throws Exception {
    compareHttp("/api/pets", 200);
    compareHttp("/api/pets?page=1&size=1", 200);
    compareHttp("/api/pets/" + catId, 200);
    compareHttp("/api/subscription-plans?petId=" + petId, 200);
    compareHttp("/api/subscription-plan-versions/" + versionId + "?petId=" + petId, 200);
    compareHttp("/api/subscription-plan-versions/" + versionId + "?petId=" + catId, 409);
    jdbc.update("UPDATE subscription_plans SET current_plan_version_id=NULL WHERE current_plan_version_id=?", versionId);
    assertThat(store.findPlanVersion(versionId).currentPlanVersionId()).isNull();
    compareHttp("/api/subscription-plan-versions/" + versionId + "?petId=" + petId, 409);
    compareHttp("/api/subscriptions", 200);
    // Frozen main also fails when every pet ID on the page is null (empty Map.of().get(null)).
    // T08 preserves this existing error; repairing the application contract is a separate task.
    compareHttp("/api/subscriptions?size=1&page=0", 500);
    compareHttp("/api/subscriptions?size=1&page=99", 200);
    compareHttp("/api/subscriptions/" + subscriptionId, 200);
    compareHttp("/api/subscriptions/" + subscriptionId + "?scheduleSize=1&schedulePage=1&commandSize=1&commandPage=1", 200);
    jdbc.update("UPDATE subscription_schedules SET effective_snapshot_id=? WHERE id=?", snapshotId, nextId);
    compareHttp("/api/subscriptions/" + subscriptionId, 200);
    jdbc.update("UPDATE subscription_schedules SET status='HELD',hold_reason='ORDER_STOCK_UNAVAILABLE' WHERE id=?", nextId);
    compareHttp("/api/subscriptions/" + subscriptionId, 200);
    jdbc.update("UPDATE subscriptions SET status='PAUSED' WHERE id=?", subscriptionId);
    compareHttp("/api/subscriptions/" + subscriptionId, 200);
    compareHttp("/api/pets/9223372036854775807", 404);
    compareHttp("/api/subscriptions/9223372036854775807", 404);
    compareHttp("/api/subscription-plan-versions/9223372036854775807?petId=" + petId, 404);
    for (String query : List.of("page=-1", "size=0", "size=101", "page=2147483647&size=100")) {
      compareHttp("/api/pets?" + query, 400);
      compareHttp("/api/subscriptions?" + query, 400);
      compareHttp("/api/subscription-plans?petId=" + petId + "&" + query, 400);
    }
    compareHttp("/api/subscriptions/" + subscriptionId + "?commandSize=0", 400);
    compareHttp("/api/subscriptions/" + subscriptionId + "?schedulePage=-1", 400);
    long realMember = memberId;
    memberId = otherMemberId;
    compareHttp("/api/pets/" + petId, 404);
    compareHttp("/api/subscriptions/" + subscriptionId, 404);
    memberId = realMember;
    assertThatThrownBy(() -> store.findSnapshot(Long.MAX_VALUE)).isInstanceOf(java.util.NoSuchElementException.class);
  }

  @Test
  void zeroRowsLegacyOwnershipAndHeldOrderedScheduleKeepTheirExistingSemantics() throws Exception {
    assertThat(store.findPets(Long.MAX_VALUE, 0, 20)).isEqualTo(legacy.findPets(Long.MAX_VALUE, 0, 20));
    assertThat(store.findSubscriptions(otherMemberId, 0, 20)).isEqualTo(legacy.findSubscriptions(otherMemberId, 0, 20));
    assertThat(store.findSalePlanVersions("NONE", today, 0, 20)).isEqualTo(legacy.findSalePlanVersions("NONE", today, 0, 20));
    long empty = subscription(petId, "ACTIVE");
    assertThat(store.findPendingChange(empty)).isEmpty();
    assertThat(store.findNextDeliverySchedule(empty)).isEmpty();
    assertThat(store.findNextSchedule(empty, today)).isEmpty();
    assertThat(store.findScheduleViews(empty, 0, 20)).isEqualTo(legacy.findScheduleViews(empty, 0, 20));
    assertThat(store.findCommandHistory(empty, 0, 20)).isEqualTo(legacy.findCommandHistory(empty, 0, 20));
    compareHttp("/api/subscriptions/" + empty, 200);
    jdbc.update("UPDATE subscriptions SET runtime_managed=false WHERE id=?", empty);
    compareHttp("/api/subscriptions/" + empty, 404);
    jdbc.update("UPDATE subscription_schedules SET status='HELD',hold_reason='MISSING_BILLING_METHOD'"
        + " WHERE subscription_id=? AND scheduled_date=?", subscriptionId, today);
    assertThat(store.findNextDeliverySchedule(subscriptionId).orElseThrow().scheduledDate()).isEqualTo(today);
    assertThat(store.findNextDeliverySchedule(subscriptionId)).isEqualTo(legacy.findNextDeliverySchedule(subscriptionId));
    compareHttp("/api/subscriptions/" + subscriptionId, 200);
    jdbc.update("UPDATE subscriptions SET status='CANCELED' WHERE id=?", subscriptionId);
    compareHttp("/api/subscriptions/" + subscriptionId, 200);
  }

  @Test
  void scalarQueriesSeeJdbcWritesInSameTransactionWithoutStaleEntityState() {
    assertThat(store.findOwnedPet(memberId, petId).breed()).isNull();
    store.updatePet(memberId, petId, "changed", true, "breed", true, new BigDecimal("9.99"), true);
    assertThat(store.findOwnedPet(memberId, petId)).isEqualTo(legacy.findOwnedPet(memberId, petId));
    assertThat(store.findOwnedPet(memberId, petId).name()).isEqualTo("changed");
    store.incrementVersion(subscriptionId, 0);
    store.setSubscriptionStatus(subscriptionId, "PAUSED");
    assertThat(store.findOwnedSubscription(memberId, subscriptionId)).isEqualTo(legacy.findOwnedSubscription(memberId, subscriptionId));
    assertThat(store.findOwnedSubscription(memberId, subscriptionId).version()).isEqualTo(1);
    store.deletePendingPlanChange(subscriptionId);
    assertThat(store.findPendingChange(subscriptionId)).isEmpty();
    store.deleteScheduleAddon(nextId, secondSkuId);
    assertThat(store.findScheduleAddons(nextId)).isEmpty();
    store.upsertScheduleAddon(nextId, secondSkuId, 10, new BigDecimal("9999999999999999.99"));
    assertThat(store.findScheduleAddons(nextId).getFirst().lineAmount()).isEqualByComparingTo("99999999999999999.90");
    store.insertCommandHistory(subscriptionId, "PAUSE", 0, 1);
    assertThat(store.findCommandHistory(subscriptionId, 0, 20)).isEqualTo(legacy.findCommandHistory(subscriptionId, 0, 20));
    long created = subscription(petId, "ACTIVE");
    assertThat(store.findOwnedSubscription(memberId, created)).isEqualTo(legacy.findOwnedSubscription(memberId, created));
  }

  @Test
  void batchedPageQueryCountIsConstantAndNeverHydratesReadEntities() {
    Statistics stats = emf.unwrap(SessionFactory.class).getStatistics();
    stats.setStatisticsEnabled(true);
    try {
      stats.clear();
      var one = service.subscriptions(memberId, 1, 1);
      assertThat(one.totalElements()).isEqualTo(3);
      assertThat(stats.getPrepareStatementCount()).isEqualTo(6);
      assertThat(stats.getEntityLoadCount()).isZero();
      for (int i = 0; i < 30; i++) subscription(petId, "ACTIVE");
      stats.clear();
      assertThat(service.subscriptions(memberId, 0, 100).totalElements()).isEqualTo(33);
      assertThat(stats.getPrepareStatementCount()).isEqualTo(6);
      assertThat(stats.getEntityLoadCount()).isZero();
      stats.clear();
      service.plans(memberId, petId, 0, 100);
      assertThat(stats.getPrepareStatementCount()).isEqualTo(5);
      assertThat(stats.getEntityLoadCount()).isZero();
      stats.clear();
      service.subscriptions(otherMemberId, 0, 100);
      assertThat(stats.getPrepareStatementCount()).isEqualTo(2);
      assertThat(store.findPlanItems(List.of())).isEmpty();
      assertThat(store.findSnapshotItems(List.of())).isEmpty();
      assertThat(store.findNextSchedules(List.of(), today)).isEmpty();
      assertThat(stats.getPrepareStatementCount()).isEqualTo(2);
    } finally {
      stats.setStatisticsEnabled(false);
    }
  }

  private long plan(String name, String petType, boolean onSale, boolean migration,
      LocalDate starts, LocalDate ends, long price) {
    jdbc.update("INSERT INTO subscription_plans(name,target_pet_type,on_sale,sale_starts_on,sale_ends_on) VALUES (?,?,?,?,?)",
        name, petType, onSale, starts, ends);
    long planId = jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
    jdbc.update("INSERT INTO plan_versions(plan_id,package_price_krw,is_migration_only) VALUES (?,?,?)", planId, price, migration);
    long id = jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
    // Insert out of SKU/cycle order to prove explicit SQL ordering survives projection conversion.
    jdbc.update("INSERT INTO plan_items(plan_version_id,sku_id,quantity) VALUES (?,?,1),(?,?,2)", id, secondSkuId, id, skuId);
    jdbc.update("INSERT INTO plan_version_delivery_cycles(plan_version_id,delivery_cycle_weeks) VALUES (?,8),(?,2),(?,4)", id, id, id);
    jdbc.update("UPDATE subscription_plans SET current_plan_version_id=? WHERE id=?", id, planId);
    return id;
  }

  private long subscription(long pet, String status) {
    long id = store.insertSubscription(memberId, versionId, 4, pet, today.minusDays(1), today.plusDays(1));
    long snapshot = store.createSnapshot(id, versionId, 4, 24000);
    store.setCurrentSnapshot(id, snapshot);
    store.setSubscriptionStatus(id, status);
    return id;
  }

  private void compareHttp(String url, int expectedStatus) throws Exception {
    String expectedBody, expectedEtag;
    try {
      routeReadsToFrozenJdbc();
      var expected = response(url);
      assertThat(expected.getStatus()).as("JDBC %s", url).isEqualTo(expectedStatus);
      expectedBody = expected.getContentAsString();
      expectedEtag = expected.getHeader("ETag");
    } finally {
      reset(store);
    }
    var actual = response(url);
    assertThat(actual.getStatus()).as("JPA %s", url).isEqualTo(expectedStatus);
    assertThat(actual.getHeader("ETag")).as(url).isEqualTo(expectedEtag);
    assertThat(actual.getContentAsString()).as(url).isEqualTo(expectedBody);
  }

  private org.springframework.mock.web.MockHttpServletResponse response(String url) throws Exception {
    return http.perform(get(url).with(authentication(new UsernamePasswordAuthenticationToken(
        new AuthenticatedMemberPrincipal(memberId), null, List.of(new SimpleGrantedAuthority("ROLE_USER"))))))
        .andReturn().getResponse();
  }

  private void routeReadsToFrozenJdbc() throws Exception {
    Set<String> converted = Set.of("findOwnedPet", "findPlanVersion", "findOwnedSubscription", "findPets",
        "findSalePlanVersions", "findPlanItems", "findDeliveryCycles", "findSnapshot", "findPendingChange",
        "scheduleAddonCount", "findScheduleAddons", "findSubscriptions", "findOwnedPets", "findSnapshots",
        "findSnapshotItems", "findNextSchedules", "findNextSchedule", "findNextDeliverySchedule",
        "findSnapshotItemDetails", "findScheduleViews", "findCommandHistory");
    for (var method : LegacySubscriptionReadReference.class.getDeclaredMethods()) {
      if (!Modifier.isPublic(method.getModifiers()) || !converted.contains(method.getName())) continue;
      var target = SubscriptionAggregateQueryPersistence.class.getMethod(method.getName(), method.getParameterTypes());
      var stub = doAnswer(invocation -> {
        try { return method.invoke(legacy, invocation.getArguments()); }
        catch (InvocationTargetException e) { throw e.getCause(); }
      }).when(store);
      Object[] matchers = new Object[method.getParameterCount()];
      for (int i = 0; i < matchers.length; i++) {
        Class<?> type = method.getParameterTypes()[i];
        matchers[i] = type == long.class ? anyLong() : type == int.class ? anyInt() : any(type);
      }
      target.invoke(stub, matchers);
    }
  }
}
