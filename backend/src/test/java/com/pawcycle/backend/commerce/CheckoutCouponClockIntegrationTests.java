package com.pawcycle.backend.commerce;

import static org.assertj.core.api.Assertions.assertThat;

import com.pawcycle.backend.commerce.checkout.persistence.CheckoutAddress;
import com.pawcycle.backend.commerce.checkout.persistence.CheckoutPersistenceAdapter;
import com.pawcycle.backend.commerce.coupon.domain.CouponEntity;
import com.pawcycle.backend.commerce.coupon.domain.MemberCouponEntity;
import com.pawcycle.backend.commerce.coupon.persistence.CouponRepository;
import com.pawcycle.backend.commerce.coupon.persistence.MemberCouponRepository;
import com.pawcycle.backend.member.domain.Member;
import com.pawcycle.backend.member.persistence.MemberRepository;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@ActiveProfiles("test")
@Import(CheckoutCouponClockIntegrationTests.FixedClock.class)
@Transactional
class CheckoutCouponClockIntegrationTests {
  private static final Instant NOW = Instant.parse("2026-08-09T00:00:00.123456Z");
  private static final LocalDateTime UTC_NOW = LocalDateTime.ofInstant(NOW, ZoneOffset.UTC);

  @Autowired private CheckoutPersistenceAdapter checkout;
  @Autowired private CouponRepository coupons;
  @Autowired private MemberCouponRepository memberCoupons;
  @Autowired private MemberRepository members;
  @Autowired private EntityManager entities;

  @ParameterizedTest
  @CsvSource({"0,1,true", "1,2,false", "-1,0,false", "-1,1,true"})
  void couponWindowIncludesStartAndExcludesEndAfterJpaRoundTrip(
      long startMicros, long endMicros, boolean valid) {
    Member member = member();
    CouponEntity coupon = coupon(startMicros, endMicros, true);
    long memberCouponId =
        memberCoupons.saveAndFlush(new MemberCouponEntity(member.getId(), coupon.getId(), UTC_NOW))
            .getId();
    entities.clear();

    CouponEntity loaded = coupons.findById(coupon.getId()).orElseThrow();
    assertThat(loaded.getValidFrom()).isEqualTo(UTC_NOW.plusNanos(startMicros * 1000));
    assertThat(loaded.getValidUntil()).isEqualTo(UTC_NOW.plusNanos(endMicros * 1000));
    assertThat(checkout.findCouponRule(member.getId(), memberCouponId) != null).isEqualTo(valid);
  }

  @Test
  void ownerStatusAndReservationCasRemainScopedToTheMember() {
    Member owner = member();
    Member other = member();
    long couponId = coupon(-1, 1, true).getId();
    long owned =
        memberCoupons.saveAndFlush(new MemberCouponEntity(owner.getId(), couponId, UTC_NOW)).getId();
    long foreign =
        memberCoupons.saveAndFlush(new MemberCouponEntity(other.getId(), couponId, UTC_NOW)).getId();
    long inactive =
        memberCoupons.saveAndFlush(
            new MemberCouponEntity(owner.getId(), coupon(-1, 1, false).getId(), UTC_NOW)).getId();
    long orderId = checkout.createOrder(
        owner.getId(), "coupon-clock-" + UUID.randomUUID(), BigDecimal.TEN, BigDecimal.ZERO,
        BigDecimal.TEN, new CheckoutAddress("test", "test", "00000", "test", null));
    entities.clear();

    assertThat(checkout.findCouponRule(owner.getId(), owned)).isNotNull();
    assertThat(checkout.findCouponRule(owner.getId(), foreign)).isNull();
    assertThat(checkout.findCouponRule(owner.getId(), Long.MAX_VALUE)).isNull();
    assertThat(checkout.findCouponRule(owner.getId(), inactive)).isNull();
    assertThat(memberCoupons.reserveIfAvailable(orderId, foreign, owner.getId())).isZero();
    assertThat(memberCoupons.reserveIfAvailable(orderId, owned, owner.getId())).isEqualTo(1);
    assertThat(memberCoupons.reserveIfAvailable(orderId, owned, owner.getId())).isZero();
    entities.clear();
    assertThat(checkout.findCouponRule(owner.getId(), owned)).isNull();
    assertThat(memberCoupons.findById(owned).orElseThrow().getStatus()).isEqualTo("RESERVED");
    assertThat(memberCoupons.findById(foreign).orElseThrow().getStatus()).isEqualTo("AVAILABLE");
    assertThat(memberCoupons.useReserved(orderId, UTC_NOW)).isEqualTo(1);
    entities.clear();
    assertThat(checkout.findCouponRule(owner.getId(), owned)).isNull();
    assertThat(memberCoupons.findById(owned).orElseThrow().getStatus()).isEqualTo("USED");
  }

  private Member member() {
    return members.saveAndFlush(new Member("coupon-clock-" + UUID.randomUUID() + "@example.test", "test-hash"));
  }

  private CouponEntity coupon(long startMicros, long endMicros, boolean active) {
    return coupons.saveAndFlush(new CouponEntity(
        "coupon-clock-" + UUID.randomUUID(), "FIXED_AMOUNT", BigDecimal.ONE, BigDecimal.ZERO,
        null, UTC_NOW.plusNanos(startMicros * 1000), UTC_NOW.plusNanos(endMicros * 1000), active));
  }

  @TestConfiguration
  static class FixedClock {
    @Bean
    @Primary
    Clock couponClock() {
      return Clock.fixed(NOW, ZoneId.of("Asia/Seoul"));
    }
  }
}
