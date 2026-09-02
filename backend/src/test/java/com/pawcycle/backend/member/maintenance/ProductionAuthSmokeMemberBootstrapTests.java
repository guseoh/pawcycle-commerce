package com.pawcycle.backend.member.maintenance;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class ProductionAuthSmokeMemberBootstrapTests {

  @Test
  void nonWebWithoutExactEnablementFailsBeforeApplicationStarts() {
    assertRejected(new String[] {"--spring.main.web-application-type=none"});
    assertRejected(
        new String[] {
          "--spring.main.web-application-type=none",
          "--pawcycle.maintenance.create-auth-smoke-member.enabled=false"
        });
    assertRejected(
        new String[] {
          "--spring.main.web-application-type=none",
          "--pawcycle.maintenance.create-auth-smoke-member.enabled=TRUE"
        });
    assertRejected(
        new String[] {
          "--spring.main.web-application-type=none",
          "--pawcycle.maintenance.create-auth-smoke-member.enabled=true",
          "--pawcycle.maintenance.create-auth-smoke-member.enabled=false"
        });
    assertRejected(
        new String[] {
          "--spring.main.web-application-type=nones",
          "--pawcycle.maintenance.create-auth-smoke-member.enabled=true"
        });
    assertRejected(
        new String[] {
          "--spring.main.web-application-type=none",
          "--spring.main.web-application-type=servlet",
          "--pawcycle.maintenance.create-auth-smoke-member.enabled=true"
        });
    assertRejected(
        new String[] {
          "--spring.main.web-application-type",
          "--pawcycle.maintenance.create-auth-smoke-member.enabled=true"
        });
  }

  @Test
  void validGateForcesNonWebFlywayAndLoggingSafetyArguments() {
    AtomicReference<String[]> launchedArguments = new AtomicReference<>();
    ByteArrayOutputStream error = new ByteArrayOutputStream();

    int result =
        ProductionAuthSmokeMemberBootstrap.runIfRequested(
            new String[] {
              "--spring.main.web-application-type=none",
              "--pawcycle.maintenance.create-auth-smoke-member.enabled=true",
              "--spring.flyway.enabled=true",
              "--spring.main.add-command-line-properties=false",
              "--logging.level.root=TRACE",
              "--debug"
            },
            Map.of(),
            Map.of(),
            arguments -> {
              launchedArguments.set(arguments);
              return 0;
            },
            new PrintStream(error, true, StandardCharsets.UTF_8));

    assertThat(result).isZero();
    assertThat(error).hasToString("");
    assertThat(launchedArguments.get())
        .contains(
            "--spring.main.web-application-type=none",
            "--pawcycle.maintenance.create-auth-smoke-member.enabled=true",
            "--spring.flyway.enabled=false",
            "--spring.main.add-command-line-properties=true",
            "--spring.main.banner-mode=off",
            "--spring.main.log-startup-info=false",
            "--logging.level.root=OFF",
            "--debug=false",
            "--trace=false",
            "--spring.jpa.show-sql=false",
            "--spring.jpa.properties.hibernate.show_sql=false")
        .doesNotContain(
            "--spring.flyway.enabled=true",
            "--spring.main.add-command-line-properties=false",
            "--logging.level.root=TRACE",
            "--debug");
  }

  @Test
  void normalWebInvocationDoesNotEnterMaintenanceBootstrap() {
    AtomicInteger launches = new AtomicInteger();
    ByteArrayOutputStream error = new ByteArrayOutputStream();

    int defaultWebResult =
        ProductionAuthSmokeMemberBootstrap.runIfRequested(
            new String[] {"--pawcycle.maintenance.create-auth-smoke-member.enabled=true"},
            Map.of(),
            Map.of(),
            arguments -> launches.incrementAndGet(),
            new PrintStream(error, true, StandardCharsets.UTF_8));
    int servletWebResult =
        ProductionAuthSmokeMemberBootstrap.runIfRequested(
            new String[] {
              "--spring.main.web-application-type=servlet",
              "--pawcycle.maintenance.create-auth-smoke-member.enabled=true"
            },
            Map.of(),
            Map.of(),
            arguments -> launches.incrementAndGet(),
            new PrintStream(error, true, StandardCharsets.UTF_8));

    assertThat(defaultWebResult).isEqualTo(ProductionAuthSmokeMemberBootstrap.NOT_REQUESTED);
    assertThat(servletWebResult).isEqualTo(ProductionAuthSmokeMemberBootstrap.NOT_REQUESTED);
    assertThat(launches).hasValue(0);
    assertThat(error).hasToString("");
  }

  private void assertRejected(String[] arguments) {
    AtomicInteger launches = new AtomicInteger();
    ByteArrayOutputStream error = new ByteArrayOutputStream();

    int result =
        ProductionAuthSmokeMemberBootstrap.runIfRequested(
            arguments,
            Map.of(),
            Map.of(),
            ignored -> launches.incrementAndGet(),
            new PrintStream(error, true, StandardCharsets.UTF_8));

    assertThat(result).isEqualTo(ProductionAuthSmokeMemberBootstrap.FAILURE);
    assertThat(launches).hasValue(0);
    assertThat(error.toString(StandardCharsets.UTF_8))
        .isEqualTo(ProductionAuthSmokeMemberBootstrap.ERROR_MESSAGE + System.lineSeparator());
  }
}
