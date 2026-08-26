package com.pawcycle.backend.catalog.application;

import java.io.IOException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.core.io.Resource;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

@Service
@Profile("local-integration & !test & !production & !prod")
public class DemoProductDetailSectionFixtureService {

    static final String DEFAULT_FIXTURE_LOCATION = "classpath:catalog/demo-product-detail-sections.json";

    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;
    private String fixtureLocation;

    @Autowired
    public DemoProductDetailSectionFixtureService(
            JdbcTemplate jdbc,
            @Value("${pawcycle.local-demo-catalog.detail-sections:" + DEFAULT_FIXTURE_LOCATION + "}") String fixtureLocation) {
        this.jdbc = jdbc;
        this.objectMapper = new ObjectMapper();
        this.fixtureLocation = fixtureLocation;
    }

    public DemoProductDetailSectionFixtureService(JdbcTemplate jdbc) {
        this(jdbc, DEFAULT_FIXTURE_LOCATION);
    }

    @Transactional
    public void bootstrap() {
        DetailSectionManifest manifest = loadFixture();
        Map<String, List<DetailSectionFixture>> sectionsByCatalogKey = validateAndGroup(manifest);
        Map<String, Long> productIds = findProducts(sectionsByCatalogKey.keySet());
        if (productIds.size() != sectionsByCatalogKey.size()) {
            Set<String> missing = new HashSet<>(sectionsByCatalogKey.keySet());
            missing.removeAll(productIds.keySet());
            throw fixtureError("존재하지 않는 catalogKey: " + missing);
        }

        for (List<DetailSectionFixture> sections : sectionsByCatalogKey.values()) {
            long productId = productIds.get(sections.getFirst().catalogKey());
            for (DetailSectionFixture section : sections) {
                ensureSection(productId, section);
            }
        }
    }

    private Map<String, Long> findProducts(Set<String> catalogKeys) {
        String placeholders = String.join(",", java.util.Collections.nCopies(catalogKeys.size(), "?"));
        Map<String, Long> productIds = new HashMap<>();
        for (Map<String, Object> row : jdbc.queryForList(
                "SELECT id,catalog_key FROM products WHERE catalog_key IN (" + placeholders + ") FOR UPDATE",
                catalogKeys.toArray())) {
            productIds.put((String) row.get("catalog_key"), ((Number) row.get("id")).longValue());
        }
        return productIds;
    }

    private void ensureSection(long productId, DetailSectionFixture fixture) {
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT title,body,display_order,visible FROM product_detail_sections WHERE product_id=? AND display_order=? FOR UPDATE",
                productId, fixture.displayOrder());
        if (rows.isEmpty()) {
            Timestamp now = Timestamp.from(Instant.now());
            jdbc.update(
                    "INSERT INTO product_detail_sections(product_id,title,body,display_order,visible,created_at,updated_at) VALUES (?,?,?,?,?,?,?)",
                    productId, fixture.title(), fixture.body(), fixture.displayOrder(), fixture.visible(), now, now);
            return;
        }
        if (rows.size() != 1 || !matches(rows.getFirst(), fixture)) {
            throw fixtureError("상품 상세 section 충돌: product_id=" + productId + ", displayOrder=" + fixture.displayOrder());
        }
    }

    private boolean matches(Map<String, Object> row, DetailSectionFixture fixture) {
        return Objects.equals(fixture.title(), row.get("title"))
                && Objects.equals(fixture.body(), row.get("body"))
                && ((Number) row.get("display_order")).intValue() == fixture.displayOrder()
                && fixture.visible() == trueValue(row.get("visible"));
    }

    private Map<String, List<DetailSectionFixture>> validateAndGroup(DetailSectionManifest manifest) {
        if (manifest == null || manifest.version() != 1 || manifest.sections() == null || manifest.sections().isEmpty()) {
            throw fixtureError("상품 상세 section fixture version 또는 sections가 올바르지 않습니다");
        }
        Map<String, List<DetailSectionFixture>> grouped = new java.util.LinkedHashMap<>();
        Set<String> businessKeys = new HashSet<>();
        for (DetailSectionFixture section : manifest.sections()) {
            if (section == null || section.catalogKey() == null || !section.catalogKey().startsWith("demo-")
                    || section.title() == null || section.title().isBlank() || section.title().length() > 200
                    || section.body() == null || section.body().isBlank() || section.body().length() > 10000
                    || section.displayOrder() < 0 || section.title().contains("<") || section.title().contains(">")
                    || section.body().contains("<") || section.body().contains(">")) {
                throw fixtureError("상품 상세 section fixture의 필드가 올바르지 않습니다");
            }
            String businessKey = section.catalogKey() + "#" + section.displayOrder();
            if (!businessKeys.add(businessKey)) {
                throw fixtureError("상품 상세 section business key가 중복됩니다: " + businessKey);
            }
            grouped.computeIfAbsent(section.catalogKey(), ignored -> new java.util.ArrayList<>()).add(section);
        }
        for (Map.Entry<String, List<DetailSectionFixture>> entry : grouped.entrySet()) {
            int count = entry.getValue().size();
            if (count < 2 || count > 4) {
                throw fixtureError("상품 상세 section은 상품별 2~4개여야 합니다: " + entry.getKey());
            }
        }
        return grouped;
    }

    private DetailSectionManifest loadFixture() {
        try {
            String location = fixtureLocation == null || fixtureLocation.isBlank()
                    ? DEFAULT_FIXTURE_LOCATION : fixtureLocation;
            Resource resource = new DefaultResourceLoader().getResource(location);
            return objectMapper.readValue(resource.getInputStream(), DetailSectionManifest.class);
        } catch (IOException | RuntimeException exception) {
            if (exception instanceof CatalogManifestImportException importException) {
                throw importException;
            }
            throw fixtureError("상품 상세 section fixture를 읽을 수 없습니다", exception);
        }
    }

    private CatalogManifestImportException fixtureError(String message) {
        return new CatalogManifestImportException("Demo Product Detail section fixture 오류: " + message);
    }

    private CatalogManifestImportException fixtureError(String message, Throwable cause) {
        return new CatalogManifestImportException("Demo Product Detail section fixture 오류: " + message, cause);
    }

    private static boolean trueValue(Object value) {
        if (value instanceof Boolean booleanValue) return booleanValue;
        return value instanceof Number number && number.intValue() == 1;
    }

    private record DetailSectionManifest(int version, List<DetailSectionFixture> sections) {}

    private record DetailSectionFixture(String catalogKey, String title, String body, int displayOrder, boolean visible) {}
}
