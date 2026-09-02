package com.pawcycle.backend.commerce;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
class BillingMethodQueryService {
  private final JdbcTemplate jdbc;
  private final TossPaymentAdapter provider;

  BillingMethodQueryService(JdbcTemplate jdbc, TossPaymentAdapter provider) {
    this.jdbc = jdbc;
    this.provider = provider;
  }

  BillingMethodResponse active(long memberId) {
    Integer count =
        jdbc.queryForObject(
            "SELECT COUNT(*) FROM billing_payment_methods WHERE member_id=? AND status='ACTIVE'",
            Integer.class,
            memberId);
    return new BillingMethodResponse("TOSS", provider.isConfigured(), count != null && count > 0);
  }
}
