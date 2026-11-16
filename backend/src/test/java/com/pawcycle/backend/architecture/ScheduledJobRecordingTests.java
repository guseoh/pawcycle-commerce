package com.pawcycle.backend.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import java.nio.file.Path;
import java.util.Set;
import java.util.TreeSet;
import org.junit.jupiter.api.Test;

/**
 * Every {@code @Scheduled} method must run its work through {@code ScheduledJobRunner}, so no job
 * can run without execution, failure, duration and last-success records again (stage 2, O4).
 */
class ScheduledJobRecordingTests {
  private static final String SCHEDULED = "org.springframework.scheduling.annotation.Scheduled";
  private static final String RUNNER = "com.pawcycle.backend.common.scheduling.ScheduledJobRunner";

  @Test
  void everyScheduledMethodRunsThroughTheJobRunner() {
    JavaClasses classes = new ClassFileImporter().importPath(Path.of("build/classes/java/main"));
    Set<String> scheduled = new TreeSet<>();
    Set<String> missing = new TreeSet<>();
    for (JavaClass type : classes) {
      for (JavaMethod method : type.getMethods()) {
        if (!method.isAnnotatedWith(SCHEDULED)) continue;
        String name = type.getSimpleName() + "#" + method.getName();
        scheduled.add(name);
        boolean callsRunner = method.getMethodCallsFromSelf().stream()
            .anyMatch(call -> call.getTargetOwner().getName().equals(RUNNER)
                && call.getName().equals("run"));
        if (!callsRunner) missing.add(name);
      }
    }
    assertThat(scheduled).hasSize(9);
    assertThat(missing).as("@Scheduled methods must call ScheduledJobRunner.run").isEmpty();
  }
}
