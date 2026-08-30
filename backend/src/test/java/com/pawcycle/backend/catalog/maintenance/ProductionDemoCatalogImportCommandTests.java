package com.pawcycle.backend.catalog.maintenance;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class ProductionDemoCatalogImportCommandTests {

	@Test
	void commandIsNotRequestedWithoutExplicitEnablement() {
		int result = ProductionDemoCatalogImportCommand.runIfRequested(
				new String[0], Map.of(), Map.of(), ignored -> 0, errorStream());

		assertThat(result).isEqualTo(ProductionDemoCatalogImportCommand.NOT_REQUESTED);
	}

	@Test
	void falseEnablementDoesNotStartAnImport() {
		int result = ProductionDemoCatalogImportCommand.runIfRequested(
				new String[] {"--pawcycle.catalog.manifest-import.enabled=false"},
				Map.of(), Map.of(), ignored -> 0, errorStream());

		assertThat(result).isEqualTo(ProductionDemoCatalogImportCommand.NOT_REQUESTED);
	}

	@Test
	void enabledCommandRequiresExplicitModeAndNonWebApplication() {
		ByteArrayOutputStream error = new ByteArrayOutputStream();
		int result = ProductionDemoCatalogImportCommand.runIfRequested(
				new String[] {"--pawcycle.catalog.manifest-import.enabled=true"},
				Map.of(), Map.of(), ignored -> 0, new PrintStream(error, true, StandardCharsets.UTF_8));

		assertThat(result).isEqualTo(ProductionDemoCatalogImportCommand.FAILURE);
		assertThat(error.toString(StandardCharsets.UTF_8)).contains(ProductionDemoCatalogImportCommand.ERROR_MESSAGE);
	}

	@Test
	void validateCommandDefaultsToDemoAndEnforcesOneShotSafetyArguments() {
		AtomicReference<String[]> captured = new AtomicReference<>();
		int result = ProductionDemoCatalogImportCommand.runIfRequested(
				new String[] {
						"--spring.main.web-application-type=none",
						"--pawcycle.catalog.manifest-import.enabled=true",
						"--pawcycle.catalog.manifest-import.mode=validate"
				},
				Map.of(), Map.of(), args -> {
					captured.set(args);
					return 0;
				}, errorStream());

		assertThat(result).isZero();
		assertThat(captured.get()).contains("--pawcycle.catalog.manifest-import.mode=validate");
		assertThat(captured.get()).contains("--pawcycle.catalog.manifest-import.target=demo");
		assertThat(captured.get()).contains("--spring.flyway.enabled=false");
		assertThat(captured.get()).contains("--spring.main.web-application-type=none");
	}

	@Test
	void customerTargetIsExplicitlyPreservedAsTheEnforcedTarget() {
		AtomicReference<String[]> captured = new AtomicReference<>();
		int result = ProductionDemoCatalogImportCommand.runIfRequested(
				new String[] {
						"--spring.main.web-application-type=none",
						"--pawcycle.catalog.manifest-import.enabled=true",
						"--pawcycle.catalog.manifest-import.target=CUSTOMER",
						"--pawcycle.catalog.manifest-import.mode=validate"
				},
				Map.of(), Map.of(), args -> {
					captured.set(args);
					return 0;
				}, errorStream());

		assertThat(result).isZero();
		assertThat(captured.get()).contains("--pawcycle.catalog.manifest-import.target=customer");
		assertThat(captured.get()).doesNotContain("--pawcycle.catalog.manifest-import.target=CUSTOMER");
	}

	@Test
	void invalidTargetFailsBeforeStartingTheApplication() {
		AtomicBoolean called = new AtomicBoolean();
		int result = ProductionDemoCatalogImportCommand.runIfRequested(
				new String[] {
						"--spring.main.web-application-type=none",
						"--pawcycle.catalog.manifest-import.enabled=true",
						"--pawcycle.catalog.manifest-import.target=unknown",
						"--pawcycle.catalog.manifest-import.mode=validate"
				},
				Map.of(), Map.of(), args -> {
					called.set(true);
					return 0;
				}, errorStream());

		assertThat(result).isEqualTo(ProductionDemoCatalogImportCommand.FAILURE);
		assertThat(called).isFalse();
	}

	@Test
	void applyCommandFailsWithoutExplicitCommandLineConfirmation() {
		AtomicBoolean called = new AtomicBoolean();
		int result = ProductionDemoCatalogImportCommand.runIfRequested(
				new String[] {
						"--spring.main.web-application-type=none",
						"--pawcycle.catalog.manifest-import.enabled=true",
						"--pawcycle.catalog.manifest-import.target=customer",
						"--pawcycle.catalog.manifest-import.mode=apply"
				},
				Map.of("pawcycle.catalog.manifest-import.confirm-apply", "true"),
				Map.of(), args -> {
					called.set(true);
					return 0;
				}, errorStream());

		assertThat(result).isEqualTo(ProductionDemoCatalogImportCommand.FAILURE);
		assertThat(called).isFalse();
	}

	@Test
	void applyCommandRunsOnlyWithExplicitCommandLineConfirmation() {
		AtomicReference<String[]> captured = new AtomicReference<>();
		int result = ProductionDemoCatalogImportCommand.runIfRequested(
				new String[] {
						"--spring.main.web-application-type=none",
						"--pawcycle.catalog.manifest-import.enabled=true",
						"--pawcycle.catalog.manifest-import.target=customer",
						"--pawcycle.catalog.manifest-import.mode=apply",
						"--pawcycle.catalog.manifest-import.confirm-apply=true"
				},
				Map.of(), Map.of(), args -> {
					captured.set(args);
					return 0;
				}, errorStream());

		assertThat(result).isZero();
		assertThat(captured.get()).contains("--pawcycle.catalog.manifest-import.mode=apply");
		assertThat(captured.get()).contains("--pawcycle.catalog.manifest-import.target=customer");
		assertThat(captured.get()).contains("--pawcycle.catalog.manifest-import.confirm-apply=true");
	}

	private PrintStream errorStream() {
		return new PrintStream(new ByteArrayOutputStream(), true, StandardCharsets.UTF_8);
	}
}
