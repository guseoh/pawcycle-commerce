package com.pawcycle.backend.commerce;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

import com.pawcycle.backend.commerce.audit.application.AdminAuditService;
import com.pawcycle.backend.commerce.coupon.api.*;
import com.pawcycle.backend.commerce.coupon.application.*;
import com.pawcycle.backend.commerce.coupon.model.*;
import com.pawcycle.backend.commerce.coupon.persistence.CouponPersistenceAdapter;
import com.pawcycle.backend.commerce.coupon.persistence.CouponWriteCommand;
import com.pawcycle.backend.commerce.membership.api.*;
import com.pawcycle.backend.commerce.membership.application.*;
import com.pawcycle.backend.commerce.membership.model.*;
import com.pawcycle.backend.commerce.membership.persistence.*;
import com.pawcycle.backend.member.application.AuthenticatedMemberPrincipal;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import tools.jackson.databind.ObjectMapper;

class MemberBenefitResponseBoundaryTests {
  private final ObjectMapper json = new ObjectMapper();
  private static final Timestamp FROM = Timestamp.valueOf("2026-10-18 01:02:03.123456");
  private static final Timestamp UNTIL = Timestamp.valueOf("2027-01-02 03:04:05.999999");
  static IntStream couponMasks() { return IntStream.range(0, 128); }
  static IntStream memberMasks() { return IntStream.range(0, 64); }
  static IntStream gradeMasks() { return IntStream.range(0, 16); }
  private static <T> T value(int mask, int bit, T value) { return (mask & (1 << bit)) == 0 ? null : value; }

  @ParameterizedTest @MethodSource("couponMasks")
  void couponMatchesFrozenAcrossEveryNullableFieldPattern(int mask) throws Exception {
    var view = new CouponView(Long.MAX_VALUE, value(mask, 0, "한글 coupon"), value(mask, 1, "PERCENTAGE"),
        value(mask, 2, new BigDecimal("12.3400")), value(mask, 3, new BigDecimal("567.80")),
        value(mask, 4, new BigDecimal("9999999999.99")), value(mask, 5, FROM), value(mask, 6, UNTIL), (mask & 1) == 0);
    var actual = CouponResponse.from(view);
    assertThat(actual).isEqualTo(FrozenMemberBenefitResponses.coupon(view));
    assertThat(json.writeValueAsString(actual)).isEqualTo(json.writeValueAsString(FrozenMemberBenefitResponses.coupon(view)));
    assertThat(actual.validFrom()).isSameAs(view.validFrom());
    assertThat(actual.validUntil()).isSameAs(view.validUntil());
    assertThat(actual.discountValue()).isSameAs(view.discountValue());
  }

  @ParameterizedTest @MethodSource("memberMasks")
  void memberCouponMatchesFrozenAcrossEveryNullableFieldPattern(int mask) throws Exception {
    var view = new MemberCouponView(73, Long.MAX_VALUE, value(mask, 0, "member coupon"), value(mask, 1, "FIXED_AMOUNT"),
        value(mask, 2, new BigDecimal("0.1000")), value(mask, 3, "RESERVED"), value(mask, 4, FROM), value(mask, 5, UNTIL));
    var actual = MemberCouponResponse.from(view);
    assertThat(actual).isEqualTo(FrozenMemberBenefitResponses.memberCoupon(view));
    assertThat(json.writeValueAsString(actual)).isEqualTo(json.writeValueAsString(FrozenMemberBenefitResponses.memberCoupon(view)));
    assertThat(actual.validFrom()).isSameAs(view.validFrom());
    assertThat(actual.validUntil()).isSameAs(view.validUntil());
  }

  @ParameterizedTest @MethodSource("gradeMasks")
  void membershipAndGradeMatchFrozenNullAmountTimestampAndCouponPatterns(int mask) throws Exception {
    var member = new MembershipView(value(mask, 0, "BASIC"), value(mask, 1, "기본"),
        value(mask, 2, new BigDecimal("123456789.0100")), value(mask, 3, FROM));
    assertThat(MembershipResponse.from(member)).isEqualTo(FrozenMemberBenefitResponses.membership(member));
    assertThat(json.writeValueAsString(MembershipResponse.from(member))).isEqualTo(json.writeValueAsString(FrozenMemberBenefitResponses.membership(member)));
    assertThat(MembershipResponse.from(member).evaluatedAt()).isSameAs(member.evaluatedAt());
    var grade = new MembershipGradeView(Long.MAX_VALUE, value(mask, 0, "VIP"), value(mask, 1, "VIP name"),
        value(mask, 2, new BigDecimal("9999999999.9900")), Integer.MAX_VALUE, (mask & 1) == 0, value(mask, 3, 91L));
    assertThat(MembershipGradeResponse.from(grade)).isEqualTo(FrozenMemberBenefitResponses.grade(grade));
    assertThat(json.writeValueAsString(MembershipGradeResponse.from(grade))).isEqualTo(json.writeValueAsString(FrozenMemberBenefitResponses.grade(grade)));
  }

  @Test
  void nullViewsKeepDereferenceFailureAndFactoriesKeepPublicConstructors() {
    assertThatThrownBy(() -> CouponResponse.from(null)).isInstanceOf(NullPointerException.class);
    assertThatThrownBy(() -> MemberCouponResponse.from(null)).isInstanceOf(NullPointerException.class);
    assertThatThrownBy(() -> MembershipResponse.from(null)).isInstanceOf(NullPointerException.class);
    assertThatThrownBy(() -> MembershipGradeResponse.from(null)).isInstanceOf(NullPointerException.class);
    assertThat(new CouponResponse(1, null, null, null, null, null, null, null, false).name()).isNull();
    assertThat(new MemberCouponResponse(1, 2, null, null, null, null, null, null).status()).isNull();
    assertThat(new MembershipResponse(null, null, null, null).evaluatedAt()).isNull();
    assertThat(new MembershipGradeResponse(1, null, null, null, 0, false, null).benefitCouponId()).isNull();
  }

  @Test
  void couponControllerCopiesAllEightNullableInputsWithoutValidationOrNormalization() {
    var service = mock(CouponAdminApplicationService.class);
    when(service.create(anyLong(), any())).thenReturn(73L);
    var controller = new AdminCouponController(service);
    var principal = new AuthenticatedMemberPrincipal(7L);
    var from = LocalDateTime.parse("2026-10-18T01:02:03.123456");
    for (int mask = 0; mask < 256; mask++) {
      var request = new CouponRequest(value(mask, 0, " coupon "), value(mask, 1, "PERCENTAGE"),
          value(mask, 2, new BigDecimal("1.2300")), value(mask, 3, new BigDecimal("4.5600")),
          value(mask, 4, new BigDecimal("7.8900")), value(mask, 5, from), value(mask, 6, from.plusSeconds(1)), value(mask, 7, false));
      var response = controller.create(principal, request);
      var command = ArgumentCaptor.forClass(CouponCreateCommand.class);
      verify(service).create(eq(7L), command.capture());
      assertThat(command.getValue()).isEqualTo(new CouponCreateCommand(request.name(), request.discountType(), request.discountValue(),
          request.minimumOrderAmount(), request.maximumDiscountAmount(), request.validFrom(), request.validUntil(), request.active()));
      assertThat(command.getValue().validFrom()).isSameAs(request.validFrom());
      assertThat(response.getStatusCode().value()).isEqualTo(201);
      assertThat(response.getHeaders().getLocation().toString()).isEqualTo("/api/admin/coupons/73");
      assertThat(response.getBody().couponId()).isEqualTo(73);
      clearInvocations(service);
    }
  }

  @Test
  void gradeControllerCopiesAllSixNullableInputsBeforeExistingServiceUnboxing() {
    var service = mock(MembershipAdminApplicationService.class);
    when(service.createGrade(anyLong(), any())).thenReturn(91L);
    var controller = new AdminMembershipController(service);
    for (int mask = 0; mask < 64; mask++) {
      var request = new MembershipGradeRequest(value(mask, 0, " CODE "), value(mask, 1, " name "),
          value(mask, 2, new BigDecimal("1.2300")), value(mask, 3, 3), value(mask, 4, false), value(mask, 5, Long.MAX_VALUE));
      var response = controller.createGrade(new AuthenticatedMemberPrincipal(7L), request);
      var command = ArgumentCaptor.forClass(MembershipGradeCreateCommand.class);
      verify(service).createGrade(eq(7L), command.capture());
      assertThat(command.getValue()).isEqualTo(new MembershipGradeCreateCommand(request.code(), request.name(),
          request.minimumPurchaseAmount(), request.displayOrder(), request.active(), request.benefitCouponId()));
      assertThat(response.getStatusCode().value()).isEqualTo(201);
      assertThat(response.getHeaders().getLocation().toString()).isEqualTo("/api/admin/membership-grades/91");
      assertThat(response.getBody().gradeId()).isEqualTo(91);
      clearInvocations(service);
    }
  }

  @Test
  void servicesKeepPersistenceCommandThenGeneratedIdAuditOrder() {
    var coupons = mock(CouponPersistenceAdapter.class);
    var audits = mock(AdminAuditService.class);
    when(coupons.create(any())).thenReturn(73L);
    var from = LocalDateTime.parse("2026-10-18T01:02:03.123456");
    var input = new CouponCreateCommand("coupon", "FIXED_AMOUNT", new BigDecimal("1.2300"), BigDecimal.ZERO, null, from, from.plusDays(1), true);
    assertThat(new CouponAdminApplicationService(coupons, audits).create(7, input)).isEqualTo(73);
    var order = inOrder(coupons, audits);
    order.verify(coupons).create(new CouponWriteCommand(input.name(), input.discountType(), input.discountValue(),
        input.minimumOrderAmount(), input.maximumDiscountAmount(), input.validFrom(), input.validUntil(), input.active()));
    order.verify(audits).append(7, "COUPON_CREATE", "COUPON", 73);
    var persistence = mock(MembershipPersistenceAdapter.class);
    when(persistence.createGrade(any())).thenReturn(91L);
    var grade = new MembershipGradeCreateCommand("VIP", "VIP name", new BigDecimal("123.4500"), 3, false, null);
    var admin = new MembershipAdminApplicationService(persistence, mock(MembershipQueryRepository.class), audits, mock(MembershipEvaluationService.class));
    assertThat(admin.createGrade(7, grade)).isEqualTo(91);
    var gradeOrder = inOrder(persistence, audits);
    gradeOrder.verify(persistence).createGrade(new CreateMembershipGradeCommand(grade.code(), grade.name(), grade.minimumPurchaseAmount(), grade.displayOrder(), grade.active(), grade.benefitCouponId()));
    gradeOrder.verify(audits).append(7, "MEMBERSHIP_GRADE_CREATE", "MEMBERSHIP_GRADE", 91);
  }

  @Test
  void readServicesReturnImmutableSnapshotsWithoutChangingOrderDuplicatesOrFallback() {
    var coupons = mock(CouponPersistenceAdapter.class);
    var queries = mock(MembershipQueryRepository.class);
    var source = new ArrayList<MemberCouponView>();
    var item = new MemberCouponView(3, 2, null, null, null, "AVAILABLE", FROM, UNTIL);
    source.add(item); source.add(item);
    when(coupons.findForMember(7)).thenReturn(source);
    var benefits = new MemberBenefitApplicationService(coupons, queries);
    var result = benefits.coupons(7);
    source.clear();
    assertThat(result).containsExactly(item, item);
    assertThatThrownBy(() -> result.clear()).isInstanceOf(UnsupportedOperationException.class);
    var basic = new MembershipView("BASIC", "Basic", BigDecimal.ZERO, null);
    when(queries.findForMember(7)).thenReturn(basic);
    assertThat(benefits.membership(7)).isSameAs(basic);
    var grade = new MembershipGradeView(9, "VIP", "name", BigDecimal.ONE, 3, true, null);
    var grades = new ArrayList<>(List.of(grade, grade));
    when(queries.findGrades()).thenReturn(grades);
    var admin = new MembershipAdminApplicationService(mock(MembershipPersistenceAdapter.class), queries, mock(AdminAuditService.class), mock(MembershipEvaluationService.class));
    var actual = admin.listGrades(); grades.clear();
    assertThat(actual).containsExactly(grade, grade);
    assertThatThrownBy(() -> actual.clear()).isInstanceOf(UnsupportedOperationException.class);
    when(coupons.findForMember(8)).thenReturn(List.of());
    assertThat(benefits.coupons(8)).isEmpty();
  }
}
