package com.pawcycle.backend.catalog.admin.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.pawcycle.backend.catalog.category.domain.Category;
import com.pawcycle.backend.catalog.category.persistence.CategoryRepository;
import com.pawcycle.backend.catalog.product.persistence.ProductRepository;
import com.pawcycle.backend.catalog.sku.persistence.SkuRepository;
import com.pawcycle.backend.catalog.product.application.ProductListCache;
import com.pawcycle.backend.member.application.AuthenticatedMemberPrincipal;
import com.pawcycle.backend.member.domain.MemberRole;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest
@ActiveProfiles("test")
class AdminCatalogConcurrencyIntegrationTests {
  private final WebApplicationContext applicationContext;
  private final ObjectMapper objectMapper;
  private final CategoryRepository categoryRepository;
  private final ProductRepository productRepository;
  private final SkuRepository skuRepository;
  private final JdbcTemplate jdbc;
  private MockMvc mockMvc;
  @Autowired private PlatformTransactionManager transactionManager;
  @MockitoSpyBean private ProductListCache cache;

  @Autowired
  AdminCatalogConcurrencyIntegrationTests(
      WebApplicationContext applicationContext,
      ObjectMapper objectMapper,
      CategoryRepository categoryRepository,
      ProductRepository productRepository,
      SkuRepository skuRepository,
      JdbcTemplate jdbc) {
    this.applicationContext = applicationContext;
    this.objectMapper = objectMapper;
    this.categoryRepository = categoryRepository;
    this.productRepository = productRepository;
    this.skuRepository = skuRepository;
    this.jdbc = jdbc;
  }

  @BeforeEach
  void setUp() {
    mockMvc =
        MockMvcBuilders.webAppContextSetup(applicationContext).apply(springSecurity()).build();
    jdbc.update(
        "INSERT IGNORE INTO members(id,email,password_hash,role) VALUES"
            + " (1,'admin-catalog-concurrency-fixture@example.test','fixture','ADMIN')");
    cleanCatalog();
  }

  @AfterEach
  void tearDown() {
    cleanCatalog();
  }

  @Test
  void rootWithExistingChildCannotMoveBelowAnotherRoot() throws Exception {
    long root = categoryRepository.saveAndFlush(new Category("Root A", "root-a", 0, true)).getId();
    long other = categoryRepository.saveAndFlush(new Category("Root B", "root-b", 0, true)).getId();
    assertThat(postJson("/api/admin/categories", """
        {"name":"Child A1","slug":"child-a1","displayOrder":0,"active":true,"parentId":%d}
        """.formatted(root)).getResponse().getStatus()).isEqualTo(201);
    int audits = jdbc.queryForObject("SELECT COUNT(*) FROM admin_audit_logs", Integer.class);
    clearInvocations(cache);

    MvcResult result = patchJson("/api/admin/categories/" + root,
        "{\"name\":\"Rejected rename\",\"parentId\":%d}".formatted(other));

    assertThat(result.getResponse().getStatus()).isEqualTo(409);
    assertThat(objectMapper.readTree(result.getResponse().getContentAsByteArray()).get("code").asText())
        .isEqualTo("CATEGORY_DEPTH_EXCEEDED");
    assertThat(jdbc.queryForObject("SELECT parent_id FROM categories WHERE id=?", Long.class, root)).isNull();
    assertThat(jdbc.queryForObject("SELECT name FROM categories WHERE id=?", String.class, root)).isEqualTo("Root A");
    assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM admin_audit_logs", Integer.class)).isEqualTo(audits);
    verify(cache, never()).invalidate();
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void childCreationAndParentMoveValidateAgainAfterWaitingForCommit(boolean createFirst) throws Exception {
    long root = root("create-race-a");
    long other = root("create-race-b");
    Request create = () -> postJson("/api/admin/categories", """
        {"name":"Concurrent child","slug":"create-race-child","displayOrder":0,"active":true,"parentId":%d}
        """.formatted(root));
    Request move = () -> patchJson("/api/admin/categories/" + root, "{\"parentId\":%d}".formatted(other));
    assertCommittedOverlap(createFirst ? create : move, createFirst ? move : create,
        createFirst ? 201 : 200, "CATEGORY_DEPTH_EXCEEDED", root);
    assertThat(jdbc.queryForObject("SELECT parent_id FROM categories WHERE id=?", Long.class, root))
        .isEqualTo(createFirst ? null : other);
    assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM categories WHERE parent_id=?", Integer.class, root))
        .isEqualTo(createFirst ? 1 : 0);
  }

  @Test
  void categoryEntryTransactionsUseReadCommittedAndInvalidateOnlyAfterVisibleCommit() throws Exception {
    int audits = auditCount();
    var committedCallbacks = new AtomicLong();
    doAnswer(invocation -> {
      connectionId(); // Verify the actual HTTP-owned transaction, without a test outer transaction.
      try (var connection = jdbc.getDataSource().getConnection();
           var statement = connection.createStatement();
           var result = statement.executeQuery("SELECT COUNT(*) FROM admin_audit_logs")) {
        assertThat(result.next()).isTrue();
        assertThat((long) result.getInt(1)).isEqualTo(audits + committedCallbacks.incrementAndGet());
      }
      return null;
    }).when(cache).invalidate();
    var created = postJson("/api/admin/categories", """
        {"name":"Cache root","slug":"cache-root","displayOrder":0,"active":true}
        """);
    assertThat(created.getResponse().getStatus()).isEqualTo(201);
    long id = objectMapper.readTree(created.getResponse().getContentAsByteArray()).get("categoryId").asLong();
    assertThat(patchJson("/api/admin/categories/" + id, "{\"name\":\"Updated cache root\"}")
        .getResponse().getStatus()).isEqualTo(200);
    assertThat(committedCallbacks.get()).isEqualTo(2);
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void competingChainMovesCannotCommitThirdDepth(boolean moveParentFirst) throws Exception {
    long a = root("chain-a");
    long b = root("chain-b");
    long c = root("chain-c");
    Request moveA = () -> patchJson("/api/admin/categories/" + a, "{\"parentId\":%d}".formatted(b));
    Request moveB = () -> patchJson("/api/admin/categories/" + b, "{\"parentId\":%d}".formatted(c));
    assertCommittedOverlap(moveParentFirst ? moveB : moveA, moveParentFirst ? moveA : moveB,
        200, "CATEGORY_DEPTH_EXCEEDED", b);
  }

  @Test
  void reciprocalMovesAcquireLowerIdFirstAndCannotCommitCycle() throws Exception {
    long a = root("cycle-a");
    long b = root("cycle-b");
    var guardReady = new CountDownLatch(1);
    var releaseGuard = new CountDownLatch(1);
    var actorsReady = new CountDownLatch(2);
    var guardConnection = new AtomicLong();
    var firstConnection = new AtomicLong();
    var secondConnection = new AtomicLong();
    int audits = auditCount();
    clearInvocations(cache);
    try (var executor = Executors.newFixedThreadPool(3)) {
      var guard = executor.submit(() -> transaction().execute(status -> {
        guardConnection.set(connectionId());
        jdbc.queryForObject("SELECT id FROM categories WHERE id=? FOR UPDATE", Long.class, a);
        guardReady.countDown();
        await(releaseGuard);
        return null;
      }));
      assertThat(guardReady.await(15, TimeUnit.SECONDS)).isTrue();
      var first = executor.submit(() -> requestInTransaction(
          () -> patchJson("/api/admin/categories/" + a, "{\"parentId\":%d}".formatted(b)),
          firstConnection, actorsReady));
      var second = executor.submit(() -> requestInTransaction(
          () -> patchJson("/api/admin/categories/" + b, "{\"parentId\":%d}".formatted(a)),
          secondConnection, actorsReady));
      try {
        assertThat(actorsReady.await(15, TimeUnit.SECONDS)).isTrue();
        assertThat(List.of(guardConnection.get(), firstConnection.get(), secondConnection.get())).doesNotHaveDuplicates();
        awaitDatabaseWait(firstConnection.get(), guardConnection.get());
        awaitDatabaseWait(secondConnection.get(), guardConnection.get());
      } finally { releaseGuard.countDown(); }
      guard.get(20, TimeUnit.SECONDS);
      var results = List.of(first.get(20, TimeUnit.SECONDS), second.get(20, TimeUnit.SECONDS));
      assertThat(results).extracting(r -> r.getResponse().getStatus()).containsExactlyInAnyOrder(200, 409);
      assertConflict(results.stream().filter(r -> r.getResponse().getStatus() == 409).findFirst().orElseThrow(),
          "CATEGORY_PARENT_CONFLICT");
    } finally { releaseGuard.countDown(); }
    assertHierarchy();
    assertThat(auditCount()).isEqualTo(audits + 1);
    verify(cache, times(1)).invalidate();
  }

  @Test
  void unrelatedHierarchyMoveCommitsWhileOtherRootsAreLocked() throws Exception {
    long a = root("disjoint-a");
    long b = root("disjoint-b");
    long c = root("disjoint-c");
    long d = root("disjoint-d");
    var firstWritten = new CountDownLatch(1);
    var releaseFirst = new CountDownLatch(1);
    clearInvocations(cache);
    try (var executor = Executors.newFixedThreadPool(2)) {
      var first = executor.submit(() -> transaction().execute(status -> {
        var result = perform(() -> patchJson("/api/admin/categories/" + a, "{\"parentId\":%d}".formatted(b)));
        firstWritten.countDown();
        await(releaseFirst);
        return result;
      }));
      try {
        assertThat(firstWritten.await(15, TimeUnit.SECONDS)).isTrue();
        var second = executor.submit(() -> patchJson("/api/admin/categories/" + c, "{\"parentId\":%d}".formatted(d)));
        assertThat(second.get(15, TimeUnit.SECONDS).getResponse().getStatus()).isEqualTo(200);
        assertThat(jdbc.queryForObject("SELECT parent_id FROM categories WHERE id=?", Long.class, a)).isNull();
        assertThat(jdbc.queryForObject("SELECT parent_id FROM categories WHERE id=?", Long.class, c)).isEqualTo(d);
        verify(cache, times(1)).invalidate();
      } finally { releaseFirst.countDown(); }
      assertThat(first.get(20, TimeUnit.SECONDS).getResponse().getStatus()).isEqualTo(200);
    } finally { releaseFirst.countDown(); }
    verify(cache, times(2)).invalidate();
    assertHierarchy();
  }

  private void assertCommittedOverlap(Request firstRequest, Request secondRequest, int firstStatus,
      String conflictCode, long observedParent) throws Exception {
    var firstWritten = new CountDownLatch(1);
    var secondReading = new CountDownLatch(1);
    var releaseFirst = new CountDownLatch(1);
    var firstConnection = new AtomicLong();
    var secondConnection = new AtomicLong();
    int audits = auditCount();
    clearInvocations(cache);
    try (var executor = Executors.newFixedThreadPool(2)) {
      var first = executor.submit(() -> transaction().execute(status -> {
        firstConnection.set(connectionId());
        var result = perform(firstRequest);
        firstWritten.countDown();
        await(releaseFirst);
        return result;
      }));
      try {
        assertThat(firstWritten.await(15, TimeUnit.SECONDS)).isTrue();
        var second = executor.submit(() -> transaction().execute(status -> {
          secondConnection.set(connectionId());
          // Establish a read before waiting: RC must see the first writer's later commit.
          jdbc.queryForObject("SELECT COUNT(*) FROM categories WHERE parent_id=?", Integer.class, observedParent);
          secondReading.countDown();
          var result = perform(secondRequest);
          assertThat(result.getResponse().getStatus()).isEqualTo(409);
          // MVC handled the conflict; explicitly end its test-owned transaction by rollback.
          status.setRollbackOnly();
          return result;
        }));
        assertThat(secondReading.await(15, TimeUnit.SECONDS)).isTrue();
        assertThat(firstConnection.get()).isNotEqualTo(secondConnection.get());
        awaitDatabaseWait(secondConnection.get(), firstConnection.get());
        verify(cache, never()).invalidate();
        releaseFirst.countDown();
        assertThat(first.get(20, TimeUnit.SECONDS).getResponse().getStatus()).isEqualTo(firstStatus);
        assertConflict(second.get(20, TimeUnit.SECONDS), conflictCode);
      } finally { releaseFirst.countDown(); }
    } finally { releaseFirst.countDown(); }
    assertHierarchy();
    assertThat(auditCount()).isEqualTo(audits + 1);
    verify(cache, times(1)).invalidate();
  }

  private MvcResult requestInTransaction(Request request, AtomicLong connection, CountDownLatch ready) {
    return transaction().execute(status -> {
      connection.set(connectionId());
      ready.countDown();
      var result = perform(request);
      assertThat(result.getResponse().getStatus()).isIn(200, 409);
      if (result.getResponse().getStatus() == 409) status.setRollbackOnly();
      return result;
    });
  }

  private TransactionTemplate transaction() {
    var transaction = new TransactionTemplate(transactionManager);
    transaction.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
    transaction.setTimeout(30);
    return transaction;
  }

  private long connectionId() {
    assertThat(jdbc.queryForObject("SELECT @@transaction_isolation", String.class)).isEqualTo("READ-COMMITTED");
    return jdbc.queryForObject("SELECT CONNECTION_ID()", Long.class);
  }

  private void awaitDatabaseWait(long waiting, long blocking) throws InterruptedException {
    long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(15);
    do {
      long waits = jdbc.queryForObject("""
          SELECT COUNT(*) FROM performance_schema.data_lock_waits w
          JOIN performance_schema.threads requester ON requester.THREAD_ID=w.REQUESTING_THREAD_ID
          JOIN performance_schema.threads blocker ON blocker.THREAD_ID=w.BLOCKING_THREAD_ID
          WHERE requester.PROCESSLIST_ID=? AND blocker.PROCESSLIST_ID=?
          """, Long.class, waiting, blocking);
      if (waits > 0) return;
      Thread.sleep(20);
    } while (System.nanoTime() < deadline);
    throw new AssertionError("Expected independent MySQL transaction to wait for hierarchy row lock");
  }

  private static void await(CountDownLatch latch) {
    try { assertThat(latch.await(20, TimeUnit.SECONDS)).isTrue(); }
    catch (InterruptedException error) { Thread.currentThread().interrupt(); throw new AssertionError(error); }
  }

  private static MvcResult perform(Request request) {
    try { return request.perform(); }
    catch (Exception error) { throw new AssertionError(error); }
  }

  private long root(String slug) {
    return categoryRepository.saveAndFlush(new Category(slug, slug, 0, true)).getId();
  }

  private int auditCount() { return jdbc.queryForObject("SELECT COUNT(*) FROM admin_audit_logs", Integer.class); }

  private void assertConflict(MvcResult result, String code) throws Exception {
    assertThat(result.getResponse().getStatus()).isEqualTo(409);
    assertThat(objectMapper.readTree(result.getResponse().getContentAsByteArray()).get("code").asText()).isEqualTo(code);
  }

  private void assertHierarchy() {
    assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
    assertThat(jdbc.queryForObject("""
        SELECT COUNT(*) FROM categories child JOIN categories parent ON parent.id=child.parent_id
        WHERE parent.parent_id IS NOT NULL
        """, Integer.class)).isZero();
  }

  @Test
  void concurrentCategoryAndSkuDuplicatesReturnOneCreatedAndOneConflict() throws Exception {
    assertConcurrentResults(
        () ->
            postJson(
                "/api/admin/categories",
                """
                {"name":"동시 카테고리","slug":"concurrent-category","displayOrder":1,"active":true}
                """),
        "CATEGORY_SLUG_CONFLICT");

    long categoryId =
        categoryRepository
            .saveAndFlush(new Category("concurrent-product", "concurrent-product", 0, true))
            .getId();
    long productId =
        objectMapper
            .readTree(
                postJson(
                        "/api/admin/products",
                        """
                        {"name":"동시 상품","shortDescription":"동시 생성 테스트","description":null,
                         "petType":"DOG","thumbnailUrl":null,"brandId":1}
                        """
                            .replace("}", ",\"categoryId\":" + categoryId + "}"))
                    .getResponse()
                    .getContentAsByteArray())
            .get("productId")
            .asLong();

    assertConcurrentResults(
        () ->
            postJson(
                "/api/admin/products/" + productId + "/skus",
                """
                {"skuCode":"CONCURRENT-SKU","name":"동시 SKU","price":1000.00,
                 "subscribable":true,"displayOrder":1,"status":"ACTIVE"}
                """),
        "SKU_CODE_CONFLICT");
  }

  @Test
  void concurrentIdenticalProductTransitionsReturnOneSuccessAndOneConflict() throws Exception {
    long categoryId =
        categoryRepository
            .saveAndFlush(new Category("concurrent-transition", "concurrent-transition", 0, true))
            .getId();
    long productId =
        objectMapper
            .readTree(
                postJson(
                        "/api/admin/products",
                        """
                        {"name":"전이 상품","shortDescription":"동시 전이 테스트","description":null,
                         "petType":"DOG","thumbnailUrl":null,"brandId":1}
                        """
                            .replace("}", ",\"categoryId\":" + categoryId + "}"))
                    .getResponse()
                    .getContentAsByteArray())
            .get("productId")
            .asLong();

    assertConcurrentResults(
        () -> patchJson("/api/admin/products/" + productId, "{\"status\":\"PUBLIC\"}"),
        "PRODUCT_STATUS_TRANSITION_CONFLICT",
        200);
  }

  private void assertConcurrentResults(Request request, String conflictCode) throws Exception {
    assertConcurrentResults(request, conflictCode, 201);
  }

  private void assertConcurrentResults(Request request, String conflictCode, int successStatus)
      throws Exception {
    CountDownLatch ready = new CountDownLatch(2);
    CountDownLatch start = new CountDownLatch(1);
    List<MvcResult> results = new ArrayList<>();
    try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
      List<Future<MvcResult>> futures =
          List.of(
              executor.submit(() -> executeTogether(request, ready, start)),
              executor.submit(() -> executeTogether(request, ready, start)));
      ready.await();
      start.countDown();
      for (Future<MvcResult> future : futures) results.add(future.get());
    }

    assertThat(results)
        .extracting(result -> result.getResponse().getStatus())
        .containsExactlyInAnyOrder(successStatus, 409);
    MvcResult conflict =
        results.stream()
            .filter(result -> result.getResponse().getStatus() == 409)
            .findFirst()
            .orElseThrow();
    JsonNode body = objectMapper.readTree(conflict.getResponse().getContentAsByteArray());
    assertThat(body.get("code").asText()).isEqualTo(conflictCode);
  }

  private MvcResult executeTogether(Request request, CountDownLatch ready, CountDownLatch start)
      throws Exception {
    ready.countDown();
    start.await();
    return request.perform();
  }

  private MvcResult postJson(String path, String body) throws Exception {
    return performJson(post(path), body);
  }

  private MvcResult patchJson(String path, String body) throws Exception {
    return performJson(
        org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch(path), body);
  }

  private MvcResult performJson(
      org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request,
      String body)
      throws Exception {
    return mockMvc
        .perform(
            request
                .with(
                    authentication(
                        new UsernamePasswordAuthenticationToken(
                            new AuthenticatedMemberPrincipal(1L, MemberRole.ADMIN),
                            null,
                            List.of(new SimpleGrantedAuthority("ROLE_ADMIN")))))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andReturn();
  }

  private void cleanCatalog() {
    jdbc.update("DELETE FROM inventory_movements");
    jdbc.update("DELETE FROM inventories");
    skuRepository.deleteAllInBatch();
    productRepository.deleteAllInBatch();
    jdbc.update("UPDATE categories SET parent_id=NULL WHERE parent_id IS NOT NULL");
    categoryRepository.deleteAllInBatch();
  }

  @FunctionalInterface
  private interface Request {
    MvcResult perform() throws Exception;
  }
}
