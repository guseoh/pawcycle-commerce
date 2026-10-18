package com.pawcycle.backend.commerce.membership.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.pawcycle.backend.commerce.FrozenMemberBenefitResponses;
import com.pawcycle.backend.commerce.coupon.api.CouponRequest;
import com.pawcycle.backend.commerce.coupon.api.CouponResponse;
import com.pawcycle.backend.commerce.coupon.api.MemberCouponResponse;
import com.pawcycle.backend.commerce.coupon.application.CouponAdminApplicationService;
import com.pawcycle.backend.commerce.coupon.application.CouponCreateCommand;
import com.pawcycle.backend.commerce.membership.api.MembershipGradeRequest;
import com.pawcycle.backend.commerce.membership.api.MembershipGradeResponse;
import com.pawcycle.backend.commerce.membership.api.MembershipResponse;
import com.pawcycle.backend.commerce.membership.application.MemberBenefitApplicationService;
import com.pawcycle.backend.commerce.membership.application.MembershipAdminApplicationService;
import com.pawcycle.backend.commerce.membership.application.MembershipGradeCreateCommand;
import com.pawcycle.backend.commerce.membership.model.MembershipGradeView;
import com.pawcycle.backend.member.application.AuthenticatedMemberPrincipal;
import com.pawcycle.backend.member.domain.MemberRole;
import com.pawcycle.backend.support.SecondaryReadFixtures;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.context.WebApplicationContext;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest(properties = {"spring.jpa.open-in-view=false", "spring.jpa.properties.hibernate.generate_statistics=true"})
@ActiveProfiles("test")
class MemberBenefitResponseMaterializationIntegrationTests {
  private static final LocalDateTime FROM = LocalDateTime.parse("2026-10-18T01:02:03.123456");
  private static final LocalDateTime UNTIL = LocalDateTime.parse("2027-10-18T04:05:06.999999");
  @Autowired CouponAdminApplicationService coupons;
  @Autowired MembershipAdminApplicationService grades;
  @Autowired MemberBenefitApplicationService benefits;
  @Autowired JdbcTemplate jdbc;
  @Autowired EntityManager entities;
  @Autowired EntityManagerFactory emf;
  @Autowired PlatformTransactionManager transactions;
  @Autowired Clock clock;
  @Autowired ObjectMapper json;
  @Autowired WebApplicationContext web;
  private final String prefix = "B403_" + UUID.randomUUID().toString().substring(0, 12) + "_";
  private long admin, member, other, empty, firstCoupon, secondCoupon, firstGrade, secondGrade;
  private MockMvc http;

  @BeforeEach
  void setUp() {
    http = MockMvcBuilders.webAppContextSetup(web).apply(springSecurity()).build();
    tx().executeWithoutResult(s -> {
      var fixtures = new SecondaryReadFixtures(jdbc, entities);
      admin = fixtures.member(); member = fixtures.member(); other = fixtures.member(); empty = fixtures.member();
    });
    firstCoupon = coupons.create(admin, coupon("first", null, true));
    secondCoupon = coupons.create(admin, coupon("second", new BigDecimal("987.65"), false));
    coupons.issue(admin, firstCoupon, member);
    coupons.issue(admin, secondCoupon, member);
    coupons.issue(admin, firstCoupon, other);
    firstGrade = grades.createGrade(admin, new MembershipGradeCreateCommand(prefix + "A", "first", new BigDecimal("123.456"), 7, false, null));
    secondGrade = grades.createGrade(admin, new MembershipGradeCreateCommand(prefix + "B", "second", new BigDecimal("234.56"), 7, true, firstCoupon));
    jdbc.update("INSERT INTO member_memberships(member_id,grade_id,evaluated_purchase_amount,evaluated_at) VALUES (?,?,12345.67,?)",
        member, secondGrade, Timestamp.valueOf("2026-10-18 01:02:03.123456"));
  }

  @AfterEach
  void cleanUp() {
    tx().executeWithoutResult(s -> {
      jdbc.update("DELETE FROM admin_audit_logs WHERE admin_id=?", admin);
      jdbc.update("DELETE FROM member_coupons WHERE member_id IN (?,?,?,?)", admin, member, other, empty);
      jdbc.update("DELETE FROM member_memberships WHERE member_id IN (?,?,?,?)", admin, member, other, empty);
      jdbc.update("DELETE FROM membership_grades WHERE code LIKE ?", prefix + "%");
      jdbc.update("DELETE FROM coupons WHERE name LIKE ?", prefix + "%");
      jdbc.update("DELETE FROM members WHERE id IN (?,?,?,?)", admin, member, other, empty);
    });
  }

  @Test
  void allScalarOutputsKeepReadCountsSortMemberScopeUtcMicrosAndDetachedFrozenJson() throws Exception {
    Statistics stats = emf.unwrap(SessionFactory.class).getStatistics();
    stats.clear();
    var couponViews = coupons.list();
    assertThat(stats.getPrepareStatementCount()).isEqualTo(1);
    stats.clear();
    var memberViews = benefits.coupons(member);
    assertThat(stats.getPrepareStatementCount()).isEqualTo(1);
    assertThat(memberViews).extracting(v -> v.couponId()).containsExactly(secondCoupon, firstCoupon);
    assertThat(benefits.coupons(other)).extracting(v -> v.couponId()).containsExactly(firstCoupon);
    assertThat(benefits.coupons(empty)).isEmpty();
    assertThatThrownBy(() -> memberViews.clear()).isInstanceOf(UnsupportedOperationException.class);
    stats.clear();
    var gradeViews = grades.listGrades();
    assertThat(stats.getPrepareStatementCount()).isEqualTo(1);
    assertThat(gradeViews).isEqualTo(legacy().findGrades());
    assertThat(gradeViews.stream().filter(v -> v.gradeId() == firstGrade || v.gradeId() == secondGrade).toList())
        .extracting(MembershipGradeView::gradeId).containsExactly(firstGrade, secondGrade);
    assertThatThrownBy(() -> gradeViews.clear()).isInstanceOf(UnsupportedOperationException.class);
    stats.clear();
    var membership = benefits.membership(member);
    assertThat(stats.getPrepareStatementCount()).isEqualTo(1);
    var frozenMembership = legacy().findForMember(member);
    assertThat(membership).isEqualTo(frozenMembership);
    assertThat(membership.evaluatedAt().getNanos()).isEqualTo(123456000);
    stats.clear();
    var fallback = benefits.membership(empty);
    assertThat(stats.getPrepareStatementCount()).isEqualTo(2);
    assertThat(fallback.code()).isEqualTo("BASIC");
    assertThat(fallback.evaluatedAt()).isNull();
    assertThat(fallback.evaluatedPurchaseAmount()).isEqualByComparingTo(BigDecimal.ZERO);
    var first = couponViews.stream().filter(v -> v.couponId() == firstCoupon).findFirst().orElseThrow();
    assertThat(first.validFrom()).isEqualTo(Timestamp.from(FROM.withNano(0).toInstant(ZoneOffset.UTC)));
    assertThat(first.validUntil()).isEqualTo(Timestamp.from(UNTIL.withNano(0).toInstant(ZoneOffset.UTC)));
    assertThat(first.discountValue()).isEqualTo(new BigDecimal("123.46"));
    assertThat(first.maximumDiscountAmount()).isNull();
    unbound(); stats.clear();
    assertThat(json.writeValueAsString(couponViews.stream().map(CouponResponse::from).toList()))
        .isEqualTo(json.writeValueAsString(couponViews.stream().map(FrozenMemberBenefitResponses::coupon).toList()));
    assertThat(json.writeValueAsString(memberViews.stream().map(MemberCouponResponse::from).toList()))
        .isEqualTo(json.writeValueAsString(memberViews.stream().map(FrozenMemberBenefitResponses::memberCoupon).toList()));
    assertThat(json.writeValueAsString(gradeViews.stream().map(MembershipGradeResponse::from).toList()))
        .isEqualTo(json.writeValueAsString(gradeViews.stream().map(FrozenMemberBenefitResponses::grade).toList()));
    assertThat(json.writeValueAsString(MembershipResponse.from(membership)))
        .isEqualTo(json.writeValueAsString(FrozenMemberBenefitResponses.membership(frozenMembership)));
    assertThat(MembershipResponse.from(membership).evaluatedAt()).isSameAs(membership.evaluatedAt());
    assertThat(json.writeValueAsString(MembershipResponse.from(fallback)))
        .isEqualTo(json.writeValueAsString(FrozenMemberBenefitResponses.membership(fallback)));
    assertThat(stats.getPrepareStatementCount()).isZero();
    assertThat(stats.getEntityLoadCount()).isZero();
  }

  @Test
  void allFourHttpReadsKeepFrozenWireBodiesMemberScopeAuthorizationAndCsrf() throws Exception {
    String expectedCoupons = json.writeValueAsString(coupons.list().stream().map(FrozenMemberBenefitResponses::coupon).toList());
    String expectedMember = json.writeValueAsString(benefits.coupons(member).stream().map(FrozenMemberBenefitResponses::memberCoupon).toList());
    String expectedGrade = json.writeValueAsString(legacy().findGrades().stream().map(FrozenMemberBenefitResponses::grade).toList());
    String expectedMembership = json.writeValueAsString(FrozenMemberBenefitResponses.membership(legacy().findForMember(member)));
    assertThat(body("/api/admin/coupons", admin, true)).isEqualTo(expectedCoupons);
    assertThat(body("/api/coupons", member, false)).isEqualTo(expectedMember);
    assertThat(body("/api/admin/membership-grades", admin, true)).isEqualTo(expectedGrade);
    assertThat(body("/api/membership", member, false)).isEqualTo(expectedMembership);
    assertThat(body("/api/coupons", empty, false)).isEqualTo("[]");
    for (String path : List.of("/api/coupons", "/api/membership", "/api/admin/coupons", "/api/admin/membership-grades")) {
      http.perform(get(path)).andExpect(status().isUnauthorized());
    }
    for (String path : List.of("/api/admin/coupons", "/api/admin/membership-grades")) {
      http.perform(get(path).with(as(member, false))).andExpect(status().isForbidden());
      http.perform(post(path).with(as(admin, true)).contentType(MediaType.APPLICATION_JSON).content("{}"))
          .andExpect(status().isForbidden());
    }
  }

  @Test
  void validatedHttpCreateKeepsGeneratedIdLocationDecimalNullRangeAndAudit() throws Exception {
    var request = new CouponRequest(prefix + "http", "PERCENTAGE", new BigDecimal("12.345"), new BigDecimal("45.67"), null, FROM, UNTIL, true);
    var result = http.perform(post("/api/admin/coupons").with(as(admin, true)).with(csrf()).contentType(MediaType.APPLICATION_JSON)
        .content(json.writeValueAsString(request))).andExpect(status().isCreated()).andReturn().getResponse();
    long id = json.readTree(result.getContentAsString()).get("couponId").asLong();
    assertThat(id).isPositive();
    assertThat(result.getHeader("Location")).isEqualTo("/api/admin/coupons/" + id);
    assertThat(jdbc.queryForObject("SELECT discount_value FROM coupons WHERE id=?", BigDecimal.class, id)).isEqualTo(new BigDecimal("12.35"));
    assertThat(jdbc.queryForObject("SELECT maximum_discount_amount FROM coupons WHERE id=?", BigDecimal.class, id)).isNull();
    assertThat(jdbc.queryForObject("SELECT MICROSECOND(valid_from)+MICROSECOND(valid_until) FROM coupons WHERE id=?", Integer.class, id)).isZero();
    assertThat(audit("COUPON_CREATE", id)).isOne();
    var grade = new MembershipGradeRequest(prefix + "HTTP", "HTTP grade", new BigDecimal("234.567"), 8, false, id);
    var created = http.perform(post("/api/admin/membership-grades").with(as(admin, true)).with(csrf()).contentType(MediaType.APPLICATION_JSON)
        .content(json.writeValueAsString(grade))).andExpect(status().isCreated()).andReturn().getResponse();
    long gradeId = json.readTree(created.getContentAsString()).get("gradeId").asLong();
    assertThat(gradeId).isPositive();
    assertThat(created.getHeader("Location")).isEqualTo("/api/admin/membership-grades/" + gradeId);
    assertThat(jdbc.queryForObject("SELECT minimum_purchase_amount FROM membership_grades WHERE id=?", BigDecimal.class, gradeId)).isEqualTo(new BigDecimal("234.57"));
    assertThat(jdbc.queryForObject("SELECT benefit_coupon_id FROM membership_grades WHERE id=?", Long.class, gradeId)).isEqualTo(id);
    assertThat(audit("MEMBERSHIP_GRADE_CREATE", gradeId)).isOne();
    int auditBefore = jdbc.queryForObject("SELECT COUNT(*) FROM admin_audit_logs WHERE admin_id=?", Integer.class, admin);
    var invalid = new CouponRequest(prefix + "invalid", "FIXED_AMOUNT", BigDecimal.ONE, BigDecimal.ZERO, null, UNTIL, FROM, true);
    http.perform(post("/api/admin/coupons").with(as(admin, true)).with(csrf()).contentType(MediaType.APPLICATION_JSON)
        .content(json.writeValueAsString(invalid))).andExpect(status().isBadRequest());
    http.perform(post("/api/admin/membership-grades").with(as(admin, true)).with(csrf()).contentType(MediaType.APPLICATION_JSON)
        .content("{}" )).andExpect(status().isBadRequest());
    assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM admin_audit_logs WHERE admin_id=?", Integer.class, admin)).isEqualTo(auditBefore);
    assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM coupons WHERE name=?", Integer.class, prefix + "invalid")).isZero();
  }

  @Test
  void failureAfterBothAuditFlushesRollsBackGeneratedRowsAndAuditInTheRequiredTransaction() {
    long[] ids = new long[2];
    int before = jdbc.queryForObject("SELECT COUNT(*) FROM admin_audit_logs WHERE admin_id=?", Integer.class, admin);
    assertThatThrownBy(() -> tx().executeWithoutResult(s -> {
      ids[0] = coupons.create(admin, coupon("rollback", null, true));
      ids[1] = grades.createGrade(admin, new MembershipGradeCreateCommand(prefix + "ROLL", "rollback", BigDecimal.ONE, 9, true, ids[0]));
      assertThat(ids[0]).isPositive(); assertThat(ids[1]).isPositive();
      assertThat(audit("COUPON_CREATE", ids[0])).isOne();
      assertThat(audit("MEMBERSHIP_GRADE_CREATE", ids[1])).isOne();
      throw new IllegalStateException("failure after audit flush");
    })).isInstanceOf(IllegalStateException.class).hasMessage("failure after audit flush");
    assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM coupons WHERE id=?", Integer.class, ids[0])).isZero();
    assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM membership_grades WHERE id=?", Integer.class, ids[1])).isZero();
    assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM admin_audit_logs WHERE admin_id=?", Integer.class, admin)).isEqualTo(before);
  }

  private CouponCreateCommand coupon(String suffix, BigDecimal maximum, boolean active) {
    return new CouponCreateCommand(prefix + suffix, "FIXED_AMOUNT", new BigDecimal("123.456"), new BigDecimal("45.67"), maximum, FROM, UNTIL, active);
  }
  private int audit(String action, long id) {
    return jdbc.queryForObject("SELECT COUNT(*) FROM admin_audit_logs WHERE admin_id=? AND action=? AND target_id=?", Integer.class, admin, action, id);
  }
  private String body(String path, long id, boolean adminRole) throws Exception {
    return http.perform(get(path).with(as(id, adminRole))).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
  }
  private RequestPostProcessor as(long id, boolean adminRole) {
    return authentication(new UsernamePasswordAuthenticationToken(new AuthenticatedMemberPrincipal(id, adminRole ? MemberRole.ADMIN : MemberRole.USER), null,
        List.of(new SimpleGrantedAuthority(adminRole ? "ROLE_ADMIN" : "ROLE_USER"))));
  }
  private void unbound() {
    assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
    assertThat(TransactionSynchronizationManager.hasResource(emf)).isFalse();
  }
  private TransactionTemplate tx() { return new TransactionTemplate(transactions); }
  private LegacyMembershipPersistenceAdapter legacy() { return new LegacyMembershipPersistenceAdapter(jdbc, clock); }
}
