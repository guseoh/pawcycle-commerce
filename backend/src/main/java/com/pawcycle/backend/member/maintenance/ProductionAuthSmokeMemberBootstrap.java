package com.pawcycle.backend.member.maintenance;

import com.pawcycle.backend.PawcycleBackendApplication;
import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import org.springframework.boot.Banner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.context.ConfigurableApplicationContext;

public final class ProductionAuthSmokeMemberBootstrap {

	public static final int NOT_REQUESTED = -1;
	static final int FAILURE = 2;
	static final String ERROR_MESSAGE = "ERROR: production auth smoke member maintenance failed";

	private static final String WEB_APPLICATION_TYPE = "spring.main.web-application-type";
	private static final String ENABLED = "pawcycle.maintenance.create-auth-smoke-member.enabled";
	private static final String FLYWAY_ENABLED = "spring.flyway.enabled";
	private static final Map<String, String> ENVIRONMENT_KEYS = Map.of(
			WEB_APPLICATION_TYPE, "SPRING_MAIN_WEB_APPLICATION_TYPE",
			ENABLED, "PAWCYCLE_MAINTENANCE_CREATE_AUTH_SMOKE_MEMBER_ENABLED");
	private static final List<String> ENFORCED_ARGUMENTS = List.of(
			option(WEB_APPLICATION_TYPE, "none"),
			option(ENABLED, "true"),
			option(FLYWAY_ENABLED, "false"),
			option("spring.main.add-command-line-properties", "true"),
			option("spring.main.banner-mode", "off"),
			option("spring.main.log-startup-info", "false"),
			option("logging.level.root", "OFF"),
			option("debug", "false"),
			option("trace", "false"),
			option("spring.jpa.show-sql", "false"),
			option("spring.jpa.properties.hibernate.show_sql", "false"));
	private static final List<String> ENFORCED_KEYS = ENFORCED_ARGUMENTS.stream()
			.map(ProductionAuthSmokeMemberBootstrap::optionKey)
			.toList();

	private ProductionAuthSmokeMemberBootstrap() {
	}

	public static int runIfRequested(String[] args) {
		return runIfRequested(
				args,
				systemPropertyValues(),
				System.getenv(),
				ProductionAuthSmokeMemberBootstrap::runApplication,
				System.err);
	}

	static int runIfRequested(
			String[] args,
			Map<String, String> systemProperties,
			Map<String, String> environment,
			MaintenanceApplication application,
			PrintStream error) {
		ResolvedProperty webApplicationType = resolve(
				args, systemProperties, environment, WEB_APPLICATION_TYPE);
		if (!webApplicationType.present()) {
			return NOT_REQUESTED;
		}
		if (webApplicationType.ambiguous()) {
			error.println(ERROR_MESSAGE);
			return FAILURE;
		}
		String normalizedWebApplicationType = webApplicationType.value().toLowerCase(Locale.ROOT);
		if ("servlet".equals(normalizedWebApplicationType)) {
			return NOT_REQUESTED;
		}
		if (!"none".equals(normalizedWebApplicationType)) {
			error.println(ERROR_MESSAGE);
			return FAILURE;
		}

		ResolvedProperty enabled = resolve(args, systemProperties, environment, ENABLED);
		if (!enabled.present()
				|| enabled.ambiguous()
				|| !"true".equals(enabled.value())) {
			error.println(ERROR_MESSAGE);
			return FAILURE;
		}

		try {
			return application.run(enforceArguments(args));
		} catch (RuntimeException exception) {
			error.println(ERROR_MESSAGE);
			return FAILURE;
		}
	}

	private static int runApplication(String[] args) {
		SpringApplication application = new SpringApplication(PawcycleBackendApplication.class);
		application.setWebApplicationType(WebApplicationType.NONE);
		application.setAddCommandLineProperties(true);
		application.setBannerMode(Banner.Mode.OFF);
		application.setLogStartupInfo(false);
		PrintStream output = System.out;
		PrintStream error = System.err;
		try (PrintStream suppressedOutput =
						new PrintStream(OutputStream.nullOutputStream(), true, StandardCharsets.UTF_8);
				PrintStream suppressedError =
						new PrintStream(OutputStream.nullOutputStream(), true, StandardCharsets.UTF_8)) {
			System.setOut(suppressedOutput);
			System.setErr(suppressedError);
			try (ConfigurableApplicationContext ignored = application.run(args)) {
				// The command writes its PASS result to the suppressed process stream.
			} finally {
				System.setOut(output);
				System.setErr(error);
			}
		} catch (Throwable ignored) {
			throw new ProductionAuthSmokeMemberCreationException();
		}
		output.println(ProductionAuthSmokeMemberCommand.PASS_MESSAGE);
		return 0;
	}

	private static Map<String, String> systemPropertyValues() {
		Map<String, String> values = new HashMap<>();
		addSystemProperty(values, WEB_APPLICATION_TYPE);
		addSystemProperty(values, ENABLED);
		return values;
	}

	private static void addSystemProperty(Map<String, String> values, String key) {
		String value = System.getProperty(key);
		if (value != null) {
			values.put(key, value);
		}
	}

	private static String[] enforceArguments(String[] args) {
		List<String> enforced = new ArrayList<>();
		for (String argument : args) {
			if (!isEnforcedArgument(argument)) {
				enforced.add(argument);
			}
		}
		enforced.addAll(ENFORCED_ARGUMENTS);
		return enforced.toArray(String[]::new);
	}

	private static boolean isEnforcedArgument(String argument) {
		if ("--debug".equals(argument) || "--trace".equals(argument)) {
			return true;
		}
		for (String key : ENFORCED_KEYS) {
			if (argument.equals("--" + key) || argument.startsWith("--" + key + "=")) {
				return true;
			}
		}
		return false;
	}

	private static ResolvedProperty resolve(
			String[] args,
			Map<String, String> systemProperties,
			Map<String, String> environment,
			String key) {
		List<String> commandLineValues = new ArrayList<>();
		String prefix = "--" + key + "=";
		for (String argument : args) {
			if (argument.equals("--" + key)) {
				commandLineValues.add("");
			} else if (argument.startsWith(prefix)) {
				commandLineValues.add(argument.substring(prefix.length()));
			}
		}
		if (!commandLineValues.isEmpty()) {
			return new ResolvedProperty(
					true,
					commandLineValues.get(commandLineValues.size() - 1),
					commandLineValues.size() != 1);
		}
		if (systemProperties.containsKey(key)) {
			return new ResolvedProperty(true, systemProperties.get(key), false);
		}
		String environmentKey = ENVIRONMENT_KEYS.get(key);
		if (environmentKey != null && environment.containsKey(environmentKey)) {
			return new ResolvedProperty(true, environment.get(environmentKey), false);
		}
		return new ResolvedProperty(false, "", false);
	}

	private static String option(String key, String value) {
		return "--" + key + "=" + value;
	}

	private static String optionKey(String argument) {
		int separator = argument.indexOf('=');
		return argument.substring(2, separator);
	}

	@FunctionalInterface
	interface MaintenanceApplication {

		int run(String[] args);
	}

	private record ResolvedProperty(boolean present, String value, boolean ambiguous) {

		private ResolvedProperty {
			Objects.requireNonNull(value);
		}
	}
}
