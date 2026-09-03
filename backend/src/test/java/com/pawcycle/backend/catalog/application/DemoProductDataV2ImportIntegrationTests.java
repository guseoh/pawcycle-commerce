package com.pawcycle.backend.catalog.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class DemoProductDataV2ImportIntegrationTests {

  private final DemoCatalogManifestImportService importService;
  private final JdbcTemplate jdbc;

  @Autowired
  DemoProductDataV2ImportIntegrationTests(
      DemoCatalogManifestImportService importService, JdbcTemplate jdbc) {
    this.importService = importService;
    this.jdbc = jdbc;
  }

  @Test
  void generatedSmallManifestImportsProductsSkusAndInventoryIdempotently() throws Exception {
    Path repositoryRoot = repositoryRoot();
    Path generatedManifest = Files.createTempFile("product-data-v2-", ".json");
    try {
      Process generator =
          new ProcessBuilder(
                  pythonCommand(),
                  repositoryRoot.resolve("scripts/generate-product-data-v2.py").toString(),
                  "--base-manifest",
                  repositoryRoot
                      .resolve("backend/src/main/resources/catalog/demo-catalog.json")
                      .toString(),
                  "--additional-products",
                  "3",
                  "--seed",
                  "20260826",
                  "--output",
                  generatedManifest.toString())
              .directory(repositoryRoot.toFile())
              .redirectErrorStream(true)
              .start();
      String output = new String(generator.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
      assertThat(generator.waitFor()).as(output).isZero();

      DemoCatalogImportResult first =
          importService.apply(generatedManifest.toUri().toString());
      DemoCatalogImportResult second =
          importService.apply(generatedManifest.toUri().toString());

      assertThat(first.productsCreated()).isEqualTo(35);
      assertThat(first.skusCreated()).isEqualTo(48);
      assertThat(first.inventoriesCreated()).isEqualTo(48);
      assertThat(second.productsCreated()).isZero();
      assertThat(second.skusCreated()).isZero();
      assertThat(second.inventoriesCreated()).isZero();
      assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM products", Integer.class)).isEqualTo(35);
      assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM skus", Integer.class)).isEqualTo(48);
      assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM inventories", Integer.class))
          .isEqualTo(48);
      assertThat(
              jdbc.queryForObject(
                  "SELECT COUNT(*) FROM products WHERE catalog_key LIKE 'demo-%'", Integer.class))
          .isEqualTo(32);
      assertThat(
              jdbc.queryForObject(
                  "SELECT COUNT(*) FROM products WHERE catalog_key LIKE 'synthetic-v2-%'",
                  Integer.class))
          .isEqualTo(3);
      assertThat(
              jdbc.queryForObject(
                  "SELECT COUNT(*) FROM inventories inventory JOIN skus sku ON"
                      + " sku.id=inventory.sku_id WHERE sku.sku_code LIKE 'SYNTH-V2-%'",
                  Integer.class))
          .isEqualTo(6);
    } finally {
      Files.deleteIfExists(generatedManifest);
    }
  }

  private Path repositoryRoot() {
    Path workingDirectory = Path.of(System.getProperty("user.dir")).toAbsolutePath();
    return Files.isDirectory(workingDirectory.resolve("scripts"))
        ? workingDirectory
        : workingDirectory.getParent();
  }

  private String pythonCommand() {
    return System.getProperty("os.name", "").toLowerCase(java.util.Locale.ROOT).contains("win")
        ? "python"
        : "python3";
  }
}
