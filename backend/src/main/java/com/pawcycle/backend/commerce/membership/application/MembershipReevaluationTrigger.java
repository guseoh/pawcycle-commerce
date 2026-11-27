package com.pawcycle.backend.commerce.membership.application;

import com.pawcycle.backend.common.scheduling.ScheduledJob;
import com.pawcycle.backend.common.scheduling.ScheduledJobRunner;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Drains membership re-evaluation requests (PAYMENT-MEMBERSHIP-001). On by default: without it
 * grades stop following paid orders. Runs on the default scheduler thread with the other database
 * jobs, one member at a time, so two requests never evaluate concurrently.
 */
@Component
@ConditionalOnProperty(
    prefix = "pawcycle.membership.reevaluation",
    name = "enabled",
    havingValue = "true",
    matchIfMissing = true)
public class MembershipReevaluationTrigger {
  private final MembershipReevaluationService reevaluation;
  private final ScheduledJobRunner jobs;
  private final int batchSize;

  public MembershipReevaluationTrigger(
      MembershipReevaluationService reevaluation,
      ScheduledJobRunner jobs,
      @Value("${pawcycle.membership.reevaluation.batch-size:100}") int batchSize) {
    if (batchSize < 1) {
      throw new IllegalArgumentException("pawcycle.membership.reevaluation.batch-size must be positive");
    }
    this.reevaluation = reevaluation;
    this.jobs = jobs;
    this.batchSize = batchSize;
  }

  @Scheduled(
      initialDelayString = "${pawcycle.membership.reevaluation.initial-delay-ms:10000}",
      fixedDelayString = "${pawcycle.membership.reevaluation.fixed-delay-ms:10000}")
  public void processDue() {
    jobs.run(ScheduledJob.MEMBERSHIP_REEVALUATION, () -> reevaluation.processDue(batchSize));
  }
}
