package com.pawcycle.backend.subscription.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import com.pawcycle.backend.commerce.order.domain.CommerceOrderEntity;
import com.pawcycle.backend.member.domain.Member;
import com.pawcycle.backend.member.persistence.MemberRepository;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class SubscriptionWriteTimeCharacterizationTests {
  @Autowired EntityManager em;
  @Autowired JdbcTemplate jdbc;
  @Autowired MemberRepository members;

  @Test
  void existingOrderMappingPreservesDirectJdbcDatetimeWallClock() {
    long member = members.saveAndFlush(new Member("t09-" + UUID.randomUUID() + "@example.test", "fixture-only")).getId();
    LocalDateTime time = LocalDateTime.parse("2026-10-07T23:59:59.123456");
    var order = new CommerceOrderEntity("t09-" + UUID.randomUUID(), member,
        BigDecimal.TEN, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.TEN,
        "fixture", "fixture", "fixture", "fixture", null, SubscriptionJdbcTime.forUtcCalendar(time));
    em.persist(order);
    em.flush();
    assertThat(jdbc.queryForObject("SELECT CAST(created_at AS CHAR) FROM orders WHERE id=?", String.class, order.getId()))
        .isEqualTo("2026-10-07 23:59:59.123456");
  }
}
