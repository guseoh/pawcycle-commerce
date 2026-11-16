package com.pawcycle.backend.subscription.automation;

import com.pawcycle.backend.common.scheduling.ScheduledJob;
import com.pawcycle.backend.common.scheduling.ScheduledJobRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Schedules delivery reminders. Kept apart from the processor so the run is recorded around the
 * processor's transaction: a commit failure counts as a failed run, not a successful one.
 */
@Component
@ConditionalOnProperty(
    prefix = "pawcycle.subscription.delivery-reminder",
    name = "enabled",
    havingValue = "true")
public class SubscriptionDeliveryReminderTrigger {
  private final SubscriptionDeliveryReminderProcessor processor;
  private final ScheduledJobRunner jobs;

  public SubscriptionDeliveryReminderTrigger(
      SubscriptionDeliveryReminderProcessor processor, ScheduledJobRunner jobs) {
    this.processor = processor;
    this.jobs = jobs;
  }

  @Scheduled(fixedDelayString = "${pawcycle.subscription.delivery-reminder.fixed-delay-ms:60000}")
  public void remind() {
    jobs.run(ScheduledJob.SUBSCRIPTION_DELIVERY_REMINDER, processor::process);
  }
}
