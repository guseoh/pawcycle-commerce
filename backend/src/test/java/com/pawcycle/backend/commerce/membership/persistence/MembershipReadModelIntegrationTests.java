package com.pawcycle.backend.commerce.membership.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.pawcycle.backend.commerce.membership.api.MembershipGradeRequest;
import com.pawcycle.backend.commerce.membership.api.MembershipGradeResponse;
import com.pawcycle.backend.commerce.membership.api.MembershipResponse;
import com.pawcycle.backend.commerce.membership.application.MemberBenefitApplicationService;
import com.pawcycle.backend.commerce.membership.application.MembershipAdminApplicationService;
import com.pawcycle.backend.member.application.AuthenticatedMemberPrincipal;
import com.pawcycle.backend.support.SecondaryReadFixtures;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Clock;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class MembershipReadModelIntegrationTests {
  @Autowired private JdbcTemplate jdbc;
  @Autowired private EntityManager entities;
  @Autowired private MembershipQueryRepository queries;
  @Autowired private MemberBenefitApplicationService benefits;
  @Autowired private MembershipAdminApplicationService admin;
  @Autowired private Clock clock;
  @Autowired private ObjectMapper json;
  @Autowired private WebApplicationContext context;
  private SecondaryReadFixtures fixtures;
  private LegacyMembershipPersistenceAdapter legacy;
  private MockMvc http;
  private long memberId;

  @BeforeEach
  void setUp() {
    fixtures = new SecondaryReadFixtures(jdbc, entities);
    legacy = new LegacyMembershipPersistenceAdapter(jdbc, clock);
    memberId = fixtures.member();
    http = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
  }

  @Test
  void gradesKeepDisplayOrderIdNullableCouponAndAdminJson() throws Exception {
    long coupon = coupon();
    long first = grade(7, false, null);
    long second = grade(7, true, coupon);
    long earlier = grade(2, true, null);
    assertThat(queries.findGrades()).isEqualTo(legacy.findGrades());
    assertThat(queries.findGrades().stream().filter(view -> List.of(first, second, earlier).contains(view.gradeId())).toList())
        .extracting(MembershipGradeView::gradeId).containsExactly(earlier, first, second);
    assertThat(queries.findGrades()).anySatisfy(view -> {
      assertThat(view.gradeId()).isEqualTo(first); assertThat(view.benefitCouponId()).isNull();
      assertThat(view.active()).isFalse();
    }).anySatisfy(view -> {
      assertThat(view.gradeId()).isEqualTo(second); assertThat(view.benefitCouponId()).isEqualTo(coupon);
    });
    var expected = legacy.findGrades().stream().map(view -> new MembershipGradeResponse(view.gradeId(), view.code(), view.name(),
        view.minimumPurchaseAmount(), view.displayOrder(), view.active(), view.benefitCouponId())).toList();
    String response = http.perform(get("/api/admin/membership-grades").with(principal(memberId)))
        .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
    assertThat(json.readTree(response)).isEqualTo(json.readTree(json.writeValueAsString(expected)));
  }

  @Test
  void currentGradeMemberIsolationFallbackTimestampPrecisionAndCustomerJsonMatchJdbc() throws Exception {
    long grade = grade(3, true, null);
    jdbc.update("INSERT INTO member_memberships(member_id,grade_id,evaluated_purchase_amount,evaluated_at) VALUES (?,?,12345.67,?)", memberId, grade, SecondaryReadFixtures.stamp());
    assertThat(queries.findForMember(memberId)).isEqualTo(legacy.findForMember(memberId));
    assertThat(queries.findForMember(memberId).evaluatedAt().getNanos()).isEqualTo(123456000);
    assertApi(memberId);
    long other = fixtures.member();
    assertThat(queries.findForMember(other)).isEqualTo(legacy.findForMember(other));
    assertThat(queries.findForMember(other).code()).isEqualTo("BASIC");
    assertThat(queries.findForMember(other).evaluatedAt()).isNull();
    assertThat(queries.findForMember(other).evaluatedPurchaseAmount()).isEqualByComparingTo(BigDecimal.ZERO);
    assertApi(other);
    assertThat(queries.findForMember(Long.MAX_VALUE)).isEqualTo(legacy.findForMember(Long.MAX_VALUE));
  }

  @Test
  void createAndEvaluateKeepExistingHistoryCouponAuditAndRepeatedEvaluationSemantics() {
    jdbc.update("UPDATE membership_grades SET active=false WHERE code<>'BASIC'");
    long coupon = coupon();
    BigDecimal amount = new BigDecimal("987654321.00");
    var request = new MembershipGradeRequest(code(), "T05 benefit", amount, 5, true, coupon);
    long grade = admin.createGrade(memberId, request);
    assertThat(queries.findGrades()).isEqualTo(legacy.findGrades());
    fixtures.order(memberId, "ONE_TIME", "PAID", amount, Timestamp.from(clock.instant()));
    admin.evaluate(memberId, memberId);
    admin.evaluate(memberId, memberId);
    assertThat(queries.findForMember(memberId)).isEqualTo(legacy.findForMember(memberId));
    assertThat(queries.findForMember(memberId).code()).isEqualTo(request.code());
    assertThat(queries.findForMember(memberId).evaluatedPurchaseAmount()).isEqualByComparingTo(amount);
    assertThat(jdbc.queryForObject("SELECT grade_id FROM member_memberships WHERE member_id=?", Long.class, memberId)).isEqualTo(grade);
    assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM membership_histories WHERE member_id=?", Long.class, memberId)).isEqualTo(1);
    assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM member_coupons WHERE member_id=? AND coupon_id=?", Long.class, memberId, coupon)).isEqualTo(1);
    assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM admin_audit_logs WHERE admin_id=? AND action='MEMBERSHIP_EVALUATE'", Long.class, memberId)).isEqualTo(2);
  }

  private void assertApi(long member) throws Exception {
    MembershipView view = legacy.findForMember(member);
    var expected = new MembershipResponse(view.code(), view.name(), view.evaluatedPurchaseAmount(), view.evaluatedAt());
    assertThat(benefits.membership(member)).isEqualTo(expected);
    String response = http.perform(get("/api/membership").with(principal(member))).andExpect(status().isOk())
        .andReturn().getResponse().getContentAsString();
    assertThat(json.readTree(response)).isEqualTo(json.readTree(json.writeValueAsString(expected)));
  }

  private RequestPostProcessor principal(long member) {
    return authentication(new UsernamePasswordAuthenticationToken(new AuthenticatedMemberPrincipal(member), null,
        List.of(new SimpleGrantedAuthority("ROLE_USER"), new SimpleGrantedAuthority("ROLE_ADMIN"))));
  }

  private long grade(int displayOrder, boolean active, Long coupon) {
    jdbc.update("INSERT INTO membership_grades(code,name,minimum_purchase_amount,display_order,active,benefit_coupon_id) VALUES (?,'T05 grade',1234.56,?,?,?)", code(), displayOrder, active, coupon);
    return fixtures.lastId();
  }

  private long coupon() {
    jdbc.update("INSERT INTO coupons(name,discount_type,discount_value,minimum_order_amount,valid_from,valid_until,active) VALUES ('T05 coupon','FIXED_AMOUNT',100,0,'2026-09-01','2027-09-01',true)");
    return fixtures.lastId();
  }

  private static String code() { return "T05_" + UUID.randomUUID().toString().substring(0, 12); }
}
