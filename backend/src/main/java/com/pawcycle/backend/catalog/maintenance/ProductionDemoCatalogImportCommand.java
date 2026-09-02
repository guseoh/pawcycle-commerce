package com.pawcycle.backend.catalog.maintenance;

import com.pawcycle.backend.PawcycleBackendApplication;
import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.boot.Banner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.context.ConfigurableApplicationContext;

public final class ProductionDemoCatalogImportCommand {

  public static final int NOT_REQUESTED = -1;
  public static final int FAILURE = 2;
  public static final String ERROR_MESSAGE = "ERROR: production catalog import failed";

  private static final String WEB_APPLICATION_TYPE = "spring.main.web-application-type";
  private static final String ENABLED = "pawcycle.catalog.manifest-import.enabled";
  private static final String TARGET = "pawcycle.catalog.manifest-import.target";
  private static final String MODE = "pawcycle.catalog.manifest-import.mode";
  private static final String CONFIRM_APPLY = "pawcycle.catalog.manifest-import.confirm-apply";
  private static final Map<String, String> ENVIRONMENT_KEYS =
      Map.of(
          WEB_APPLICATION_TYPE, "SPRING_MAIN_WEB_APPLICATION_TYPE",
          ENABLED, "PAWCYCLE_CATALOG_MANIFEST_IMPORT_ENABLED",
          TARGET, "PAWCYCLE_CATALOG_MANIFEST_IMPORT_TARGET",
          MODE, "PAWCYCLE_CATALOG_MANIFEST_IMPORT_MODE");
  private static final List<String> ENFORCED_ARGUMENTS =
      List.of(
          option(WEB_APPLICATION_TYPE, "none"),
          option(ENABLED, "true"),
          option("spring.flyway.enabled", "false"),
          option("spring.main.add-command-line-properties", "true"),
          option("spring.main.banner-mode", "off"),
          option("spring.main.log-startup-info", "false"),
          option("logging.level.root", "OFF"),
          option("debug", "false"),
          option("trace", "false"),
          option("spring.jpa.show-sql", "false"),
          option("spring.jpa.properties.hibernate.show_sql", "false"));
  private static final List<String> ENFORCED_KEYS =
      ENFORCED_ARGUMENTS.stream().map(ProductionDemoCatalogImportCommand::optionKey).toList();

  private ProductionDemoCatalogImportCommand() {}

  public static int runIfRequested(String[] args) {
    return runIfRequested(
        args,
        systemPropertyValues(),
        System.getenv(),
        ProductionDemoCatalogImportCommand::runApplication,
        System.err);
  }

  static int runIfRequested(
      String[] args,
      Map<String, String> systemProperties,
      Map<String, String> environment,
      ImportApplication application,
      PrintStream error) {
    ResolvedProperty enabled = resolve(args, systemProperties, environment, ENABLED);
    if (!enabled.present()) return NOT_REQUESTED;
    if (enabled.ambiguous()) {
      error.println(ERROR_MESSAGE);
      return FAILURE;
    }
    if ("false".equalsIgnoreCase(enabled.value())) return NOT_REQUESTED;
    if (!"true".equalsIgnoreCase(enabled.value())) {
      error.println(ERROR_MESSAGE);
      return FAILURE;
    }

    ResolvedProperty webApplicationType =
        resolve(args, systemProperties, environment, WEB_APPLICATION_TYPE);
    if (!webApplicationType.present()
        || webApplicationType.ambiguous()
        || !"none".equalsIgnoreCase(webApplicationType.value())) {
      error.println(ERROR_MESSAGE);
      return FAILURE;
    }
    ResolvedProperty target = resolve(args, systemProperties, environment, TARGET);
    if (!target.present()) target = new ResolvedProperty(true, "demo", false);
    if (target.ambiguous()
        || (!"demo".equalsIgnoreCase(target.value())
            && !"customer".equalsIgnoreCase(target.value()))) {
      error.println(ERROR_MESSAGE);
      return FAILURE;
    }
    ResolvedProperty mode = resolve(args, systemProperties, environment, MODE);
    if (!mode.present()
        || mode.ambiguous()
        || (!"validate".equalsIgnoreCase(mode.value())
            && !"apply".equalsIgnoreCase(mode.value()))) {
      error.println(ERROR_MESSAGE);
      return FAILURE;
    }
    if ("apply".equalsIgnoreCase(mode.value())) {
      ResolvedProperty confirmation = resolveCommandLine(args, CONFIRM_APPLY);
      if (!confirmation.present()
          || confirmation.ambiguous()
          || !"true".equalsIgnoreCase(confirmation.value())) {
        error.println(ERROR_MESSAGE);
        return FAILURE;
      }
    }

    try {
      return application.run(
          enforceArguments(args, target.value().toLowerCase(java.util.Locale.ROOT)));
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
      try (ConfigurableApplicationContext context = application.run(args)) {
        ProductionDemoCatalogImportResultHolder resultHolder =
            context.getBean(ProductionDemoCatalogImportResultHolder.class);
        if (resultHolder.summary() == null)
          throw new IllegalStateException("command result is missing");
        output.println(resultHolder.summary());
      } finally {
        System.setOut(output);
        System.setErr(error);
      }
      return 0;
    } catch (Throwable ignored) {
      System.setOut(output);
      System.setErr(error);
      throw new IllegalStateException("production catalog import failed");
    }
  }

  private static Map<String, String> systemPropertyValues() {
    Map<String, String> values = new HashMap<>();
    for (String key : ENVIRONMENT_KEYS.keySet()) {
      String value = System.getProperty(key);
      if (value != null) values.put(key, value);
    }
    return values;
  }

  private static String[] enforceArguments(String[] args, String target) {
    List<String> enforced = new ArrayList<>();
    for (String argument : args) {
      if (!isEnforcedArgument(argument)) enforced.add(argument);
    }
    enforced.addAll(ENFORCED_ARGUMENTS);
    enforced.add(option(TARGET, target));
    return enforced.toArray(String[]::new);
  }

  private static boolean isEnforcedArgument(String argument) {
    if ("--debug".equals(argument) || "--trace".equals(argument)) return true;
    if (argument.equals("--" + TARGET) || argument.startsWith("--" + TARGET + "=")) return true;
    for (String key : ENFORCED_KEYS) {
      if (argument.equals("--" + key) || argument.startsWith("--" + key + "=")) return true;
    }
    return false;
  }

  private static ResolvedProperty resolve(
      String[] args,
      Map<String, String> systemProperties,
      Map<String, String> environment,
      String key) {
    ResolvedProperty commandLine = resolveCommandLine(args, key);
    if (commandLine.present()) return commandLine;
    if (systemProperties.containsKey(key))
      return new ResolvedProperty(true, systemProperties.get(key), false);
    String environmentKey = ENVIRONMENT_KEYS.get(key);
    if (environmentKey != null && environment.containsKey(environmentKey)) {
      return new ResolvedProperty(true, environment.get(environmentKey), false);
    }
    return new ResolvedProperty(false, "", false);
  }

  private static ResolvedProperty resolveCommandLine(String[] args, String key) {
    List<String> commandLineValues = new ArrayList<>();
    String prefix = "--" + key + "=";
    for (String argument : args) {
      if (argument.equals("--" + key)) commandLineValues.add("");
      else if (argument.startsWith(prefix))
        commandLineValues.add(argument.substring(prefix.length()));
    }
    if (commandLineValues.isEmpty()) return new ResolvedProperty(false, "", false);
    return new ResolvedProperty(true, commandLineValues.getLast(), commandLineValues.size() != 1);
  }

  private static String option(String key, String value) {
    return "--" + key + "=" + value;
  }

  private static String optionKey(String argument) {
    return argument.substring(2, argument.indexOf('='));
  }

  @FunctionalInterface
  interface ImportApplication {
    int run(String[] args);
  }

  private record ResolvedProperty(boolean present, String value, boolean ambiguous) {
    private ResolvedProperty {
      Objects.requireNonNull(value);
    }
  }
}
