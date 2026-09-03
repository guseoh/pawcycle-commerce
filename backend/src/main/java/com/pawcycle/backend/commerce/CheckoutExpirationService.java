package com.pawcycle.backend.commerce;

import java.sql.Timestamp;
import java.time.Clock;
import java.util.List;
import com.pawcycle.backend.foundation.persistence.NativeQueryExecutor;
import org.springframework.stereotype.Service;

/**
 * Selects only expired READY payments; each candidate is committed by the processor independently.
 */
@Service
public class CheckoutExpirationService {
  private final NativeQueryExecutor jdbc;
  private final CheckoutExpirationProcessor processor;
  private final Clock clock;

  public CheckoutExpirationService(
      NativeQueryExecutor jdbc, CheckoutExpirationProcessor processor, Clock clock) {
    this.jdbc = jdbc;
    this.processor = processor;
    this.clock = clock;
  }

  public int expireDue(int batchSize) {
    if (batchSize < 1) throw new IllegalArgumentException("batchSize must be positive");
    List<Long> paymentIds =
        jdbc.queryForList(
            "SELECT id FROM payments WHERE type='NORMAL' AND status='READY' AND expires_at IS NOT"
                + " NULL AND expires_at<=? ORDER BY expires_at,id LIMIT ?",
            Long.class,
            Timestamp.from(clock.instant()),
            batchSize);
    int expired = 0;
    for (Long paymentId : paymentIds) if (processor.expire(paymentId)) expired++;
    return expired;
  }
}
