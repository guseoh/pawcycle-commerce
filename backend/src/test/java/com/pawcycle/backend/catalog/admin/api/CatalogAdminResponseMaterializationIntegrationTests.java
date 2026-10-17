package com.pawcycle.backend.catalog.admin.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.pawcycle.backend.catalog.admin.application.AdminCatalogMutationService;
import com.pawcycle.backend.catalog.admin.model.CatalogAdminCommands.CategoryFacetAssignCommand;
import com.pawcycle.backend.catalog.admin.model.CatalogAdminCommands.DetailSectionCreateCommand;
import com.pawcycle.backend.catalog.admin.model.CatalogAdminCommands.FacetDefinitionCreateCommand;
import com.pawcycle.backend.catalog.admin.model.CatalogAdminCommands.FacetOptionCreateCommand;
import com.pawcycle.backend.catalog.admin.model.CatalogAdminCommands.ImageCreateCommand;
import com.pawcycle.backend.catalog.admin.model.CatalogAdminCommands.OptionGroupCreateCommand;
import com.pawcycle.backend.catalog.admin.model.CatalogAdminCommands.OptionValueCreateCommand;
import com.pawcycle.backend.catalog.admin.model.CatalogAdminCommands.ProductFacetValuesCommand;
import com.pawcycle.backend.catalog.admin.model.CatalogAdminCommands.SkuOptionValuesCommand;
import com.pawcycle.backend.catalog.admin.model.CatalogAdminModels.CategoryView;
import jakarta.persistence.EntityManagerFactory;
import java.lang.reflect.RecordComponent;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.hibernate.SessionFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest(properties = {"spring.jpa.open-in-view=false", "spring.jpa.properties.hibernate.generate_statistics=true"})
@ActiveProfiles("test")
class CatalogAdminResponseMaterializationIntegrationTests {
  @Autowired private AdminCatalogMutationService mutations;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private EntityManagerFactory emf;
  @Autowired private ObjectMapper json;
  private long adminId;
  private long categoryId;
  private long productId;
  private long skuId;
  private long definitionId;

  @BeforeEach
  void setUp() {
    jdbc.update("INSERT INTO members(email,password_hash,role) VALUES ('response-boundary@example.test','fixture','ADMIN')");
    adminId = jdbc.queryForObject("SELECT id FROM members WHERE email='response-boundary@example.test'", Long.class);
    jdbc.update("INSERT INTO categories(name,slug,display_order,active) VALUES ('response-boundary','response-boundary',0,true)");
    categoryId = jdbc.queryForObject("SELECT id FROM categories WHERE slug='response-boundary'", Long.class);
    jdbc.update("INSERT INTO products(brand_id,category_id,name,short_description,pet_type,display_status)"
        + " VALUES (1,?,'response-boundary','fixture','DOG','PUBLIC')", categoryId);
    productId = jdbc.queryForObject("SELECT id FROM products WHERE name='response-boundary'", Long.class);
    jdbc.update("INSERT INTO skus(product_id,sku_code,name,price,subscribable,display_order,status)"
        + " VALUES (?,'RESPONSE-BOUNDARY','fixture',100,true,0,'ACTIVE')", productId);
    skuId = jdbc.queryForObject("SELECT id FROM skus WHERE sku_code='RESPONSE-BOUNDARY'", Long.class);
  }

  @AfterEach
  void cleanUp() {
    jdbc.update("DELETE FROM admin_audit_logs WHERE admin_id=?", adminId);
    jdbc.update("DELETE FROM sku_option_values WHERE sku_id=?", skuId);
    jdbc.update("DELETE FROM product_facet_values WHERE product_id=?", productId);
    jdbc.update("DELETE FROM product_detail_sections WHERE product_id=?", productId);
    jdbc.update("DELETE FROM product_images WHERE product_id=?", productId);
    jdbc.update("DELETE FROM product_option_values WHERE option_group_id IN (SELECT id FROM product_option_groups WHERE product_id=?)", productId);
    jdbc.update("DELETE FROM product_option_groups WHERE product_id=?", productId);
    jdbc.update("DELETE FROM skus WHERE id=?", skuId);
    jdbc.update("DELETE FROM products WHERE id=?", productId);
    jdbc.update("DELETE FROM category_facets WHERE category_id=?", categoryId);
    jdbc.update("DELETE FROM facet_options WHERE facet_definition_id=?", definitionId);
    jdbc.update("DELETE FROM facet_definitions WHERE id=?", definitionId);
    jdbc.update("DELETE FROM categories WHERE id=?", categoryId);
    jdbc.update("DELETE FROM members WHERE id=?", adminId);
  }

  @Test
  void persistedScalarAndNestedViewsMapAfterCommitWithoutSessionOrExtraSql() throws Exception {
    List<Object> views = new ArrayList<>();
    views.add(mutations.createImage(adminId, productId, new ImageCreateCommand("https://example.test/response", null, 2, "DETAIL")));
    var group = mutations.createOptionGroup(adminId, productId, new OptionGroupCreateCommand("response", 1));
    var value = mutations.createOptionValue(adminId, productId, group.optionGroupId(), new OptionValueCreateCommand("later", 2));
    mutations.createOptionValue(adminId, productId, group.optionGroupId(), new OptionValueCreateCommand("earlier", 0));
    views.add(value);
    views.add(mutations.setSkuOptionValues(adminId, productId, skuId, new SkuOptionValuesCommand(List.of(value.optionValueId()))));
    var definition = mutations.createFacetDefinition(adminId, new FacetDefinitionCreateCommand("response-boundary", "response"));
    definitionId = definition.facetDefinitionId();
    var option = mutations.createFacetOption(adminId, definitionId, new FacetOptionCreateCommand("later", 2));
    mutations.createFacetOption(adminId, definitionId, new FacetOptionCreateCommand("earlier", 0));
    views.add(option);
    views.add(mutations.assignCategoryFacet(adminId, categoryId, definitionId, new CategoryFacetAssignCommand(1)));
    views.add(mutations.setProductFacetValues(adminId, productId, new ProductFacetValuesCommand(List.of(option.facetOptionId()))));
    views.add(mutations.createDetailSection(adminId, productId, new DetailSectionCreateCommand("title", "body", 0, true)));
    var groups = mutations.optionGroups(productId);
    views.add(groups);
    views.add(groups.optionGroups().getFirst());
    assertThat(groups.optionGroups().getFirst().values()).extracting(item -> item.value()).containsExactly("earlier", "later");
    views.add(mutations.facetDefinitions());
    var populatedDefinition = mutations.facetDefinition(definitionId);
    views.add(populatedDefinition);
    assertThat(populatedDefinition.options()).extracting(item -> item.value()).containsExactly("earlier", "later");
    views.add(mutations.images(productId));
    views.add(mutations.detailSections(productId));
    views.add(mutations.categoryFacets(categoryId));
    views.add(mutations.skuOptionValues(productId, skuId));
    views.add(mutations.productFacetValues(productId));
    views.add(mutations.brand(1L));
    // The existing CategoryView mapping has no runtime producer; pin its unchanged scalar shape.
    views.add(new CategoryView(categoryId, null, "response-boundary", "response-boundary", 0, true));
    assertThat(views.stream().map(Object::getClass).distinct().count()).isEqualTo(16);

    assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
    assertThat(TransactionSynchronizationManager.hasResource(emf)).isFalse();
    var statistics = emf.unwrap(SessionFactory.class).getStatistics();
    statistics.clear();
    for (Object view : views) {
      assertMaterialized(view);
      Object expected = FrozenCatalogAdminResponseMapping.class.getDeclaredMethod("response", view.getClass()).invoke(null, view);
      Object actual = FrozenCatalogAdminResponseMapping.class.getDeclaredMethod("response", view.getClass())
          .getReturnType().getMethod("from", view.getClass()).invoke(null, view);
      assertThat(actual).isEqualTo(expected);
      assertThat(json.writeValueAsString(actual)).isEqualTo(json.writeValueAsString(expected));
    }
    assertThat(statistics.getPrepareStatementCount()).isZero();
    assertThat(statistics.getEntityLoadCount()).isZero();
    assertThat(TransactionSynchronizationManager.hasResource(emf)).isFalse();
  }

  private static void assertMaterialized(Object value) throws Exception {
    if (value == null || value instanceof String || value instanceof Number
        || value instanceof Boolean || value instanceof Instant) return;
    if (value instanceof List<?> list) {
      assertThat(value).isNotInstanceOf(org.hibernate.collection.spi.PersistentCollection.class);
      for (Object element : list) assertMaterialized(element);
      return;
    }
    assertThat(value.getClass().isRecord()).isTrue();
    for (RecordComponent component : value.getClass().getRecordComponents()) {
      assertMaterialized(component.getAccessor().invoke(value));
    }
  }
}
