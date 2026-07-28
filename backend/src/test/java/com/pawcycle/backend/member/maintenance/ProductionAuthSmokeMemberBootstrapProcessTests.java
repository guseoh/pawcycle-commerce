package com.pawcycle.backend.member.maintenance;

import static org.assertj.core.api.Assertions.assertThat;

import com.pawcycle.backend.PawcycleBackendApplication;
import com.pawcycle.backend.member.domain.Member;
import com.pawcycle.backend.member.infra.MemberRepository;
import java.net.URI;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class ProductionAuthSmokeMemberBootstrapProcessTests {

	private static final Duration PROCESS_TIMEOUT = Duration.ofSeconds(60);
	private static final String DATASOURCE_MARKER = "SENSITIVE_DATASOURCE_MARKER";
	private static final String USERNAME_MARKER = "SENSITIVE_USERNAME_MARKER";
	private static final String PASSWORD_MARKER = "SENSITIVE_PASSWORD_MARKER";

	private final MemberRepository memberRepository;
	private final PasswordEncoder passwordEncoder;
	private final JdbcTemplate jdbcTemplate;
	private final Set<String> fixtureEmails = new LinkedHashSet<>();

	@Autowired
	ProductionAuthSmokeMemberBootstrapProcessTests(
			MemberRepository memberRepository,
			PasswordEncoder passwordEncoder,
			JdbcTemplate jdbcTemplate) {
		this.memberRepository = memberRepository;
		this.passwordEncoder = passwordEncoder;
		this.jdbcTemplate = jdbcTemplate;
	}

	@AfterEach
	void cleanFixtures() {
		for (String email : fixtureEmails) {
			memberRepository.findByEmail(email).ifPresent(memberRepository::delete);
		}
	}

	@ParameterizedTest
	@ValueSource(strings = {"missing", "false", "typo", "invalid"})
	void invalidGateExitsBeforeContextAndDoesNotRevealDatabaseInput(String enabledValue) throws Exception {
		List<String> arguments = new ArrayList<>();
		arguments.add("--spring.main.web-application-type=none");
		if ("typo".equals(enabledValue)) {
			arguments.add("--pawcycle.maintenance.create-auth-smoke-member.enable=true");
		} else if (!"missing".equals(enabledValue)) {
			arguments.add("--pawcycle.maintenance.create-auth-smoke-member.enabled=" + enabledValue);
		}

		ProcessResult result = runProcess(arguments, "", true);

		assertThat(result.exitCode()).isEqualTo(ProductionAuthSmokeMemberBootstrap.FAILURE);
		assertThat(result.stdout()).isEmpty();
		assertThat(result.stderr())
				.isEqualTo(ProductionAuthSmokeMemberBootstrap.ERROR_MESSAGE + System.lineSeparator())
				.doesNotContain(DATASOURCE_MARKER, USERNAME_MARKER, PASSWORD_MARKER);
	}

	@Test
	void databaseInitializationFailureIsSanitized() throws Exception {
		ProcessResult result = runProcess(
				List.of(
						"--spring.main.web-application-type=none",
						"--pawcycle.maintenance.create-auth-smoke-member.enabled=true"),
				"",
				true);

		assertThat(result.exitCode()).isEqualTo(ProductionAuthSmokeMemberBootstrap.FAILURE);
		assertThat(result.stdout()).isEmpty();
		assertThat(result.stderr())
				.isEqualTo(ProductionAuthSmokeMemberBootstrap.ERROR_MESSAGE + System.lineSeparator())
				.doesNotContain(DATASOURCE_MARKER, USERNAME_MARKER, PASSWORD_MARKER);
	}

	@Test
	void successfulProcessForcesFlywayOffAndPrintsOnlyPassLine() throws Exception {
		String email = runtimeEmail();
		String password = runtimePassword();
		fixtureEmails.add(email);
		int migrationCountBefore = appliedMigrationCount();

		ProcessResult result = runProcess(
				List.of(
						"--spring.profiles.active=test",
						"--spring.main.web-application-type=none",
						"--pawcycle.maintenance.create-auth-smoke-member.enabled=true",
						"--spring.flyway.enabled=true",
						"--spring.main.add-command-line-properties=false",
						"--spring.flyway.locations=classpath:must-not-be-read",
						"--logging.level.root=TRACE",
						"--debug"),
				email + "\n" + password + "\n",
				false);

		assertThat(result.exitCode()).isZero();
		assertThat(result.stdout())
				.isEqualTo(ProductionAuthSmokeMemberCommand.PASS_MESSAGE + System.lineSeparator())
				.doesNotContain(email, password);
		assertThat(result.stderr()).isEmpty();
		Member member = memberRepository.findByEmail(email).orElseThrow();
		assertThat(passwordEncoder.matches(password, member.getPasswordHash())).isTrue();
		assertThat(appliedMigrationCount()).isEqualTo(migrationCountBefore);
	}

	@Test
	void duplicateProcessFailsWithoutChangingMemberOrRevealingDetails() throws Exception {
		String email = runtimeEmail();
		String originalPassword = runtimePassword();
		String presentedPassword = runtimePassword();
		fixtureEmails.add(email);
		Member existing = memberRepository.saveAndFlush(
				new Member(email, passwordEncoder.encode(originalPassword)));
		String originalHash = existing.getPasswordHash();

		ProcessResult result = runProcess(
				List.of(
						"--spring.profiles.active=test",
						"--spring.main.web-application-type=none",
						"--pawcycle.maintenance.create-auth-smoke-member.enabled=true"),
				email + "\n" + presentedPassword + "\n",
				false);

		assertThat(result.exitCode()).isEqualTo(ProductionAuthSmokeMemberBootstrap.FAILURE);
		assertThat(result.stdout()).isEmpty();
		assertThat(result.stderr())
				.isEqualTo(ProductionAuthSmokeMemberBootstrap.ERROR_MESSAGE + System.lineSeparator())
				.doesNotContain(email, originalPassword, presentedPassword, originalHash);
		Member after = memberRepository.findByEmail(email).orElseThrow();
		assertThat(after.getId()).isEqualTo(existing.getId());
		assertThat(after.getPasswordHash()).isEqualTo(originalHash);
	}

	private ProcessResult runProcess(
			List<String> applicationArguments,
			String standardInput,
			boolean useSentinelDatasource) throws Exception {
		List<String> command = new ArrayList<>();
		command.add(javaExecutable());
		command.add("-cp");
		command.add(testRuntimeClasspath());
		command.add(PawcycleBackendApplication.class.getName());
		command.addAll(applicationArguments);

		ProcessBuilder processBuilder = new ProcessBuilder(command);
		if (useSentinelDatasource) {
			processBuilder.environment().put("SPRING_DATASOURCE_URL", DATASOURCE_MARKER);
			processBuilder.environment().put("SPRING_DATASOURCE_USERNAME", USERNAME_MARKER);
			processBuilder.environment().put("SPRING_DATASOURCE_PASSWORD", PASSWORD_MARKER);
		}
		Process process = processBuilder.start();
		byte[] stdout;
		byte[] stderr;
		try (var input = process.getOutputStream()) {
			input.write(standardInput.getBytes(StandardCharsets.UTF_8));
		}
		ExecutorService readers = Executors.newFixedThreadPool(2);
		try {
			Future<byte[]> stdoutReader =
					readers.submit(() -> process.getInputStream().readAllBytes());
			Future<byte[]> stderrReader =
					readers.submit(() -> process.getErrorStream().readAllBytes());
			boolean completed = process.waitFor(PROCESS_TIMEOUT.toSeconds(), TimeUnit.SECONDS);
			if (!completed) {
				process.destroyForcibly();
				process.waitFor();
			}
			assertThat(completed).as("maintenance process completed").isTrue();
			stdout = stdoutReader.get();
			stderr = stderrReader.get();
		} finally {
			readers.shutdownNow();
		}
		return new ProcessResult(
				process.exitValue(),
				new String(stdout, StandardCharsets.UTF_8),
				new String(stderr, StandardCharsets.UTF_8));
	}

	private String testRuntimeClasspath() throws Exception {
		Set<String> entries = new LinkedHashSet<>();
		addCodeSource(entries, ProductionAuthSmokeMemberBootstrapProcessTests.class);
		addCodeSource(entries, PawcycleBackendApplication.class);
		for (String entry : System.getProperty("java.class.path").split(
				java.util.regex.Pattern.quote(System.getProperty("path.separator")))) {
			if (!entry.isBlank()) {
				entries.add(entry);
			}
		}
		ClassLoader loader = Thread.currentThread().getContextClassLoader();
		while (loader != null) {
			if (loader instanceof URLClassLoader urlClassLoader) {
				for (URL url : urlClassLoader.getURLs()) {
					if ("file".equals(url.getProtocol())) {
						entries.add(Path.of(url.toURI()).toString());
					}
				}
			}
			loader = loader.getParent();
		}
		return String.join(System.getProperty("path.separator"), entries);
	}

	private void addCodeSource(Set<String> entries, Class<?> type) throws Exception {
		URI location = type.getProtectionDomain().getCodeSource().getLocation().toURI();
		entries.add(Path.of(location).toString());
	}

	private String javaExecutable() {
		String executable = System.getProperty("os.name").startsWith("Windows") ? "java.exe" : "java";
		return Path.of(System.getProperty("java.home"), "bin", executable).toString();
	}

	private int appliedMigrationCount() {
		return jdbcTemplate.queryForObject(
				"SELECT COUNT(*) FROM flyway_schema_history WHERE success = 1",
				Integer.class);
	}

	private String runtimeEmail() {
		return "ops-019-bootstrap-" + UUID.randomUUID() + "@example.test";
	}

	private String runtimePassword() {
		return UUID.randomUUID().toString();
	}

	private record ProcessResult(int exitCode, String stdout, String stderr) {
	}
}
