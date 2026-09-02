package com.pawcycle.backend.catalog.admin.application;

import com.pawcycle.backend.catalog.admin.api.AdminCatalogRequests.DetailSectionCreate;
import com.pawcycle.backend.catalog.admin.api.AdminCatalogRequests.DetailSectionPatch;
import com.pawcycle.backend.catalog.admin.api.AdminCatalogViews;
import com.pawcycle.backend.catalog.product.infra.ProductRepository;
import com.pawcycle.backend.common.error.FieldErrorResponse;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProductDetailSectionService {
  private final JdbcTemplate jdbc;
  private final ProductRepository products;

  public ProductDetailSectionService(JdbcTemplate jdbc, ProductRepository products) {
    this.jdbc = jdbc;
    this.products = products;
  }

  @Transactional(readOnly = true)
  public AdminCatalogViews.DetailSectionList list(long productId) {
    requireProduct(productId);
    return new AdminCatalogViews.DetailSectionList(
        jdbc.query(
            """
            SELECT id,product_id,title,body,display_order,visible,created_at,updated_at
            FROM product_detail_sections WHERE product_id=? ORDER BY display_order ASC,id ASC
            """,
            (rs, rowNum) ->
                view(
                    rs.getLong("id"),
                    rs.getLong("product_id"),
                    rs.getString("title"),
                    rs.getString("body"),
                    rs.getInt("display_order"),
                    rs.getBoolean("visible"),
                    rs.getTimestamp("created_at").toInstant(),
                    rs.getTimestamp("updated_at").toInstant()),
            productId));
  }

  @Transactional
  public AdminCatalogViews.DetailSection create(long productId, DetailSectionCreate request) {
    requireProduct(productId);
    Timestamp now = Timestamp.from(Instant.now());
    jdbc.update(
        """
        INSERT INTO product_detail_sections(product_id,title,body,display_order,visible,created_at,updated_at)
        VALUES (?,?,?,?,?,?,?)
        """,
        productId,
        request.title(),
        request.body(),
        request.displayOrder(),
        request.visible(),
        now,
        now);
    long id = jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
    return find(id, productId);
  }

  @Transactional
  public AdminCatalogViews.DetailSection update(
      long productId, long sectionId, DetailSectionPatch request) {
    validate(request);
    requireProduct(productId);
    Map<String, Object> current =
        jdbc
            .queryForList(
                "SELECT * FROM product_detail_sections WHERE id=? AND product_id=? FOR UPDATE",
                sectionId,
                productId)
            .stream()
            .findFirst()
            .orElseThrow(() -> notFound(sectionId));
    String title = request.isTitlePresent() ? request.getTitle() : (String) current.get("title");
    String body = request.isBodyPresent() ? request.getBody() : (String) current.get("body");
    Integer displayOrder =
        request.isDisplayOrderPresent()
            ? request.getDisplayOrder()
            : ((Number) current.get("display_order")).intValue();
    Boolean visible =
        request.isVisiblePresent() ? request.getVisible() : (Boolean) current.get("visible");
    jdbc.update(
        "UPDATE product_detail_sections SET title=?,body=?,display_order=?,visible=?,updated_at=?"
            + " WHERE id=? AND product_id=?",
        title,
        body,
        displayOrder,
        visible,
        Timestamp.from(Instant.now()),
        sectionId,
        productId);
    return find(sectionId, productId);
  }

  @Transactional
  public void delete(long productId, long sectionId) {
    requireProduct(productId);
    if (jdbc.update(
            "DELETE FROM product_detail_sections WHERE id=? AND product_id=?", sectionId, productId)
        != 1) {
      throw notFound(sectionId);
    }
  }

  private AdminCatalogViews.DetailSection find(long sectionId, long productId) {
    return jdbc
        .query(
            "SELECT id,product_id,title,body,display_order,visible,created_at,updated_at FROM"
                + " product_detail_sections WHERE id=? AND product_id=?",
            (rs, rowNum) ->
                view(
                    rs.getLong("id"),
                    rs.getLong("product_id"),
                    rs.getString("title"),
                    rs.getString("body"),
                    rs.getInt("display_order"),
                    rs.getBoolean("visible"),
                    rs.getTimestamp("created_at").toInstant(),
                    rs.getTimestamp("updated_at").toInstant()),
            sectionId,
            productId)
        .stream()
        .findFirst()
        .orElseThrow(() -> notFound(sectionId));
  }

  private void requireProduct(long productId) {
    if (!products.existsById(productId))
      throw new AdminCatalogNotFoundException("PRODUCT_NOT_FOUND", "상품을 확인할 수 없습니다.");
  }

  private AdminCatalogNotFoundException notFound(long sectionId) {
    return new AdminCatalogNotFoundException("DETAIL_SECTION_NOT_FOUND", "상품 상세 섹션을 확인할 수 없습니다.");
  }

  private void validate(DetailSectionPatch request) {
    List<FieldErrorResponse> errors = new ArrayList<>();
    if (!request.isTitlePresent()
        && !request.isBodyPresent()
        && !request.isDisplayOrderPresent()
        && !request.isVisiblePresent()) {
      errors.add(new FieldErrorResponse("request", "수정할 필드를 하나 이상 입력해 주세요."));
    }
    if (request.isTitlePresent()
        && (request.getTitle() == null
            || request.getTitle().isBlank()
            || request.getTitle().length() > 200)) {
      errors.add(new FieldErrorResponse("title", "필수 입력이며 200자 이하여야 합니다."));
    }
    if (request.isBodyPresent()
        && (request.getBody() == null
            || request.getBody().isBlank()
            || request.getBody().length() > 10000)) {
      errors.add(new FieldErrorResponse("body", "필수 입력이며 10000자 이하여야 합니다."));
    }
    if (request.isDisplayOrderPresent()
        && (request.getDisplayOrder() == null || request.getDisplayOrder() < 0)) {
      errors.add(new FieldErrorResponse("displayOrder", "0 이상이어야 합니다."));
    }
    if (request.isVisiblePresent() && request.getVisible() == null) {
      errors.add(new FieldErrorResponse("visible", "필수 입력입니다."));
    }
    if (!errors.isEmpty()) throw new AdminCatalogValidationException(errors);
  }

  private AdminCatalogViews.DetailSection view(
      long id,
      long productId,
      String title,
      String body,
      int displayOrder,
      boolean visible,
      Instant createdAt,
      Instant updatedAt) {
    return new AdminCatalogViews.DetailSection(
        id, productId, title, body, displayOrder, visible, createdAt, updatedAt);
  }
}
