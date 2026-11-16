package com.pawcycle.backend.subscription.automation;

import static org.assertj.core.api.Assertions.assertThat;

import com.pawcycle.backend.common.scheduling.SchedulerNames;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.task.TaskSchedulingAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.Scheduled;

/**
 * Scheduler threads by role (stage 2, O7), with the application's scheduling configuration and
 * Spring Boot's auto-configured default scheduler: database jobs share the default thread, metric
 * refreshes and provider-call jobs each have their own, so a blocked provider call delays neither.
 */
class SchedulerThreadCharacterizationTests {

  @Test
  void eachRoleRunsOnItsOwnThread() {
    new ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(TaskSchedulingAutoConfiguration.class))
        .withUserConfiguration(SchedulingConfiguration.class, Jobs.class)
        .run(context -> {
          Jobs.Recorder recorder = context.getBean(Jobs.Recorder.class);
          assertThat(recorder.allRan.await(10, TimeUnit.SECONDS)).isTrue();
          assertThat(recorder.databaseThreads).containsExactly("scheduling-1");
          assertThat(recorder.metricsThreads).singleElement().asString().startsWith("metrics-refresh-");
          assertThat(recorder.providerThreads).singleElement().asString().startsWith("provider-call-");
        });
  }

  @Test
  void blockedProviderCallDelaysNeitherDatabaseJobsNorMetrics() {
    new ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(TaskSchedulingAutoConfiguration.class))
        .withUserConfiguration(SchedulingConfiguration.class, Jobs.class)
        .run(context -> {
          Jobs.Recorder recorder = context.getBean(Jobs.Recorder.class);
          assertThat(recorder.providerEntered.await(10, TimeUnit.SECONDS)).isTrue();
          int database = recorder.databaseRuns.get();
          int metrics = recorder.metricsRuns.get();
          // The provider job is still waiting; the other roles keep running meanwhile.
          Thread.sleep(500);
          assertThat(recorder.providerRelease.getCount()).isOne();
          assertThat(recorder.databaseRuns.get()).isGreaterThan(database + 2);
          assertThat(recorder.metricsRuns.get()).isGreaterThan(metrics + 2);
          recorder.providerRelease.countDown();
        });
  }

  @Configuration(proxyBeanMethods = false)
  static class Jobs {
    @Bean
    Recorder recorder() {
      return new Recorder();
    }

    static class Recorder {
      final Set<String> databaseThreads = ConcurrentHashMap.newKeySet();
      final Set<String> metricsThreads = ConcurrentHashMap.newKeySet();
      final Set<String> providerThreads = ConcurrentHashMap.newKeySet();
      final AtomicInteger databaseRuns = new AtomicInteger();
      final AtomicInteger metricsRuns = new AtomicInteger();
      final CountDownLatch allRan = new CountDownLatch(3);
      final CountDownLatch providerEntered = new CountDownLatch(1);
      final CountDownLatch providerRelease = new CountDownLatch(1);

      @Scheduled(fixedDelay = 20)
      void databaseJob() {
        databaseThreads.add(Thread.currentThread().getName());
        if (databaseRuns.incrementAndGet() == 1) allRan.countDown();
      }

      @Scheduled(scheduler = SchedulerNames.METRICS, fixedDelay = 20)
      void metricsRefresh() {
        metricsThreads.add(Thread.currentThread().getName());
        if (metricsRuns.incrementAndGet() == 1) allRan.countDown();
      }

      @Scheduled(scheduler = SchedulerNames.PROVIDER_CALLS, fixedDelay = 20)
      void providerCall() throws InterruptedException {
        providerThreads.add(Thread.currentThread().getName());
        allRan.countDown();
        providerEntered.countDown();
        providerRelease.await(5, TimeUnit.SECONDS);
      }
    }
  }
}
