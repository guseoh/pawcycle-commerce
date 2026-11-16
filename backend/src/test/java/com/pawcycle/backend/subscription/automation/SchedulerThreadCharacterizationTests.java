package com.pawcycle.backend.subscription.automation;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.task.TaskSchedulingAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.Scheduled;

/**
 * Characterizes which threads run the application's default {@code @Scheduled} jobs (stage 2, O7):
 * the application's own scheduling configuration plus Spring Boot's auto-configured scheduler.
 */
class SchedulerThreadCharacterizationTests {

  @Test
  void defaultScheduledJobsShareOneSchedulerThread() {
    new ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(TaskSchedulingAutoConfiguration.class))
        .withUserConfiguration(SchedulingConfiguration.class, Jobs.class)
        .run(context -> {
          Jobs.Recorder recorder = context.getBean(Jobs.Recorder.class);
          assertThat(recorder.ran.await(10, TimeUnit.SECONDS)).isTrue();
          // Current behaviour, not a target: every default job runs one after another on a single
          // thread, so a slow job (a provider call can wait 10 seconds) delays all the others.
          // The pool decision is separate; change this assertion together with that decision.
          assertThat(recorder.threads).containsExactly("scheduling-1");
        });
  }

  @Configuration(proxyBeanMethods = false)
  static class Jobs {
    @Bean
    Recorder recorder() {
      return new Recorder();
    }

    static class Recorder {
      final Set<String> threads = ConcurrentHashMap.newKeySet();
      final CountDownLatch ran = new CountDownLatch(4);

      @Scheduled(fixedDelay = 50)
      void first() throws InterruptedException {
        threads.add(Thread.currentThread().getName());
        Thread.sleep(200);
        ran.countDown();
      }

      @Scheduled(fixedDelay = 50)
      void second() throws InterruptedException {
        threads.add(Thread.currentThread().getName());
        Thread.sleep(200);
        ran.countDown();
      }
    }
  }
}
