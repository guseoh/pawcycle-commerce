package com.pawcycle.backend.common.scheduling;

/**
 * Scheduler threads by role (stage 2, O7). Each role has its own single thread so that a slow job
 * of one role cannot delay another:
 *
 * <ul>
 *   <li>default scheduler ({@code scheduling-1}): database business jobs, one after another;
 *   <li>{@link #METRICS}: metric refreshes, so observation is never starved by business jobs;
 *   <li>{@link #PROVIDER_CALLS}: jobs that wait on an external provider (billing charge, up to
 *       10 seconds per call), so they cannot hold up expiration or order automation.
 * </ul>
 */
public final class SchedulerNames {
  public static final String METRICS = "metricsTaskScheduler";
  public static final String PROVIDER_CALLS = "providerCallTaskScheduler";

  private SchedulerNames() {}
}
