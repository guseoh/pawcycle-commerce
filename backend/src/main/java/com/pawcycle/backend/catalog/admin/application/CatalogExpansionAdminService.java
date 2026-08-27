package com.pawcycle.backend.catalog.admin.application;

import com.pawcycle.backend.catalog.admin.api.AdminCatalogRequests;
import com.pawcycle.backend.catalog.admin.api.AdminCatalogViews;
import com.pawcycle.backend.catalog.product.application.ProductListCacheInvalidator;
import com.pawcycle.backend.common.error.FieldErrorResponse;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** V24-specific administrative catalog operations, implemented with small explicit SQL transactions. */
@Service
public class CatalogExpansionAdminService {
	private final JdbcTemplate jdbc;
	private final ProductListCacheInvalidator cacheInvalidator;

	public CatalogExpansionAdminService(JdbcTemplate jdbc, ProductListCacheInvalidator cacheInvalidator) {
		this.jdbc = jdbc;
		this.cacheInvalidator = cacheInvalidator;
	}

	@Transactional(readOnly = true)
	public AdminCatalogViews.Brand brand(long brandId) {
		return jdbc.query(
				"SELECT id,name,slug,logo_url,active,display_order FROM brands WHERE id=?",
				(rs, n) -> brand(rs.getLong(1), rs.getString(2), rs.getString(3), rs.getString(4), rs.getBoolean(5), rs.getInt(6)),
				brandId)
				.stream().findFirst().orElseThrow(() -> missing("BRAND_NOT_FOUND", "브랜드를 확인할 수 없습니다."));
	}

	@Transactional
	public AdminCatalogViews.Brand updateBrand(long brandId, AdminCatalogRequests.BrandPatch request) {
		requirePatch(request.isNamePresent() || request.isSlugPresent() || request.isLogoUrlPresent()
				|| request.isActivePresent() || request.isDisplayOrderPresent());
		AdminCatalogViews.Brand current = brand(brandId);
		String name = request.isNamePresent() ? requiredText(request.getName(), "name", 150) : current.name();
		String slug = request.isSlugPresent() ? slug(request.getSlug(), "slug") : current.slug();
		String logoUrl = request.isLogoUrlPresent() ? nullableText(request.getLogoUrl(), "logoUrl", 2048) : current.logoUrl();
		boolean active = request.isActivePresent() ? requiredBoolean(request.getActive(), "active") : current.active();
		int displayOrder = request.isDisplayOrderPresent()
				? nonNegativeRequired(request.getDisplayOrder(), "displayOrder") : current.displayOrder();
		try {
			jdbc.update("UPDATE brands SET name=?,slug=?,logo_url=?,active=?,display_order=? WHERE id=?",
					name, slug, logoUrl, active, displayOrder, brandId);
		} catch (DataIntegrityViolationException exception) {
			throw conflict("BRAND_SLUG_CONFLICT", "이미 사용 중인 브랜드 slug입니다.");
		}
		cacheInvalidator.invalidateAfterCommit();
		return brand(brandId);
	}

	@Transactional(readOnly = true)
	public AdminCatalogViews.ImageList images(long productId) {
		requireProduct(productId);
		return new AdminCatalogViews.ImageList(jdbc.query(
				"SELECT id,product_id,image_url,alt_text,display_order,image_type FROM product_images WHERE product_id=? ORDER BY display_order,id",
				(rs, n) -> image(rs), productId));
	}

	@Transactional
	public AdminCatalogViews.Image createImage(long productId, AdminCatalogRequests.ImageCreate request) {
		requireProduct(productId);
		try {
			jdbc.update("INSERT INTO product_images(product_id,image_url,alt_text,display_order,image_type) VALUES (?,?,?,?,?)",
					productId, request.imageUrl(), request.altText(), request.displayOrder(), request.imageType());
		} catch (DataIntegrityViolationException exception) {
			throw conflict("PRODUCT_MAIN_IMAGE_CONFLICT", "상품에는 MAIN 이미지를 하나만 지정할 수 있습니다.");
		}
		long imageId = jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
		cacheInvalidator.invalidateAfterCommit();
		return image(productId, imageId);
	}

	@Transactional
	public AdminCatalogViews.Image updateImage(long productId, long imageId, AdminCatalogRequests.ImagePatch request) {
		requirePatch(request.isImageUrlPresent() || request.isAltTextPresent()
				|| request.isDisplayOrderPresent() || request.isImageTypePresent());
		AdminCatalogViews.Image current = image(productId, imageId);
		String imageUrl = request.isImageUrlPresent() ? requiredText(request.getImageUrl(), "imageUrl", 2048) : current.imageUrl();
		String altText = request.isAltTextPresent() ? nullableText(request.getAltText(), "altText", 500) : current.altText();
		int displayOrder = request.isDisplayOrderPresent()
				? nonNegativeRequired(request.getDisplayOrder(), "displayOrder") : current.displayOrder();
		String imageType = request.isImageTypePresent() ? imageType(request.getImageType()) : current.imageType();
		try {
			jdbc.update("UPDATE product_images SET image_url=?,alt_text=?,display_order=?,image_type=? WHERE id=? AND product_id=?",
					imageUrl, altText, displayOrder, imageType, imageId, productId);
		} catch (DataIntegrityViolationException exception) {
			throw conflict("PRODUCT_MAIN_IMAGE_CONFLICT", "상품에는 MAIN 이미지를 하나만 지정할 수 있습니다.");
		}
		cacheInvalidator.invalidateAfterCommit();
		return image(productId, imageId);
	}

	@Transactional
	public void deleteImage(long productId, long imageId) {
		if (jdbc.update("DELETE FROM product_images WHERE id=? AND product_id=?", imageId, productId) == 0) {
			throw missing("PRODUCT_IMAGE_NOT_FOUND", "상품 이미지를 확인할 수 없습니다.");
		}
		cacheInvalidator.invalidateAfterCommit();
	}

	@Transactional(readOnly = true)
	public AdminCatalogViews.OptionGroupList optionGroups(long productId) {
		requireProduct(productId);
		List<AdminCatalogViews.OptionGroup> groups = jdbc.query(
				"SELECT id,product_id,name,display_order FROM product_option_groups WHERE product_id=? ORDER BY display_order,id",
				(rs, n) -> new AdminCatalogViews.OptionGroup(rs.getLong(1), rs.getLong(2), rs.getString(3), rs.getInt(4), List.of()),
				productId);
		return new AdminCatalogViews.OptionGroupList(groups.stream()
				.map(group -> new AdminCatalogViews.OptionGroup(group.optionGroupId(), group.productId(), group.name(),
						group.displayOrder(), optionValues(group.optionGroupId())))
				.toList());
	}

	@Transactional
	public AdminCatalogViews.OptionGroup createOptionGroup(long productId, AdminCatalogRequests.OptionGroupCreate request) {
		lockProduct(productId);
		Long groupCount = jdbc.queryForObject("SELECT COUNT(*) FROM product_option_groups WHERE product_id=?", Long.class, productId);
		if (groupCount != null && groupCount >= 2) {
			throw conflict("OPTION_GROUP_LIMIT_EXCEEDED", "상품당 옵션 그룹은 최대 2개까지 지정할 수 있습니다.");
		}
		try {
			jdbc.update("INSERT INTO product_option_groups(product_id,name,display_order) VALUES (?,?,?)",
					productId, request.name(), request.displayOrder());
		} catch (DataIntegrityViolationException exception) {
			throw conflict("OPTION_GROUP_NAME_CONFLICT", "상품 내 옵션 그룹 이름은 중복될 수 없습니다.");
		}
		long id = jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
		return optionGroup(productId, id);
	}

	@Transactional
	public AdminCatalogViews.OptionGroup updateOptionGroup(long productId, long groupId, AdminCatalogRequests.OptionGroupPatch request) {
		requirePatch(request.isNamePresent() || request.isDisplayOrderPresent());
		AdminCatalogViews.OptionGroup current = optionGroup(productId, groupId);
		String name = request.isNamePresent() ? requiredText(request.getName(), "name", 100) : current.name();
		int displayOrder = request.isDisplayOrderPresent()
				? nonNegativeRequired(request.getDisplayOrder(), "displayOrder") : current.displayOrder();
		try {
			jdbc.update("UPDATE product_option_groups SET name=?,display_order=? WHERE id=? AND product_id=?",
					name, displayOrder, groupId, productId);
		} catch (DataIntegrityViolationException exception) {
			throw conflict("OPTION_GROUP_NAME_CONFLICT", "상품 내 옵션 그룹 이름은 중복될 수 없습니다.");
		}
		return optionGroup(productId, groupId);
	}

	@Transactional
	public void deleteOptionGroup(long productId, long groupId) {
		try {
			if (jdbc.update("DELETE FROM product_option_groups WHERE id=? AND product_id=?", groupId, productId) == 0) {
				throw missing("OPTION_GROUP_NOT_FOUND", "옵션 그룹을 확인할 수 없습니다.");
			}
		} catch (DataIntegrityViolationException exception) {
			throw conflict("OPTION_GROUP_IN_USE", "SKU에 연결된 옵션 그룹은 삭제할 수 없습니다.");
		}
	}

	@Transactional
	public AdminCatalogViews.OptionValue createOptionValue(long productId, long groupId, AdminCatalogRequests.OptionValueCreate request) {
		optionGroup(productId, groupId);
		try {
			jdbc.update("INSERT INTO product_option_values(option_group_id,value,display_order) VALUES (?,?,?)",
					groupId, request.value(), request.displayOrder());
		} catch (DataIntegrityViolationException exception) {
			throw conflict("OPTION_VALUE_CONFLICT", "옵션 그룹 내 값은 중복될 수 없습니다.");
		}
		long id = jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
		return optionValue(groupId, id);
	}

	@Transactional
	public AdminCatalogViews.OptionValue updateOptionValue(long productId, long groupId, long valueId, AdminCatalogRequests.OptionValuePatch request) {
		requirePatch(request.isValuePresent() || request.isDisplayOrderPresent());
		AdminCatalogViews.OptionValue current = optionValueForProduct(productId, groupId, valueId);
		String value = request.isValuePresent() ? requiredText(request.getValue(), "value", 100) : current.value();
		int displayOrder = request.isDisplayOrderPresent()
				? nonNegativeRequired(request.getDisplayOrder(), "displayOrder") : current.displayOrder();
		try {
			jdbc.update("UPDATE product_option_values SET value=?,display_order=? WHERE id=? AND option_group_id=?",
					value, displayOrder, valueId, groupId);
		} catch (DataIntegrityViolationException exception) {
			throw conflict("OPTION_VALUE_CONFLICT", "옵션 그룹 내 값은 중복될 수 없습니다.");
		}
		return optionValue(groupId, valueId);
	}

	@Transactional
	public void deleteOptionValue(long productId, long groupId, long valueId) {
		optionValueForProduct(productId, groupId, valueId);
		try {
			jdbc.update("DELETE FROM product_option_values WHERE id=? AND option_group_id=?", valueId, groupId);
		} catch (DataIntegrityViolationException exception) {
			throw conflict("OPTION_VALUE_IN_USE", "SKU에 연결된 옵션 값은 삭제할 수 없습니다.");
		}
	}

	@Transactional
	public AdminCatalogViews.SkuOptionValues setSkuOptionValues(long productId, long skuId, AdminCatalogRequests.SkuOptionValues request) {
		lockProduct(productId);
		requireSku(productId, skuId);
		List<Long> ids = distinct(request.optionValueIds(), "optionValueIds");
		if (!ids.isEmpty()) {
			List<Long> groups = jdbc.query(
					"SELECT v.option_group_id FROM product_option_values v JOIN product_option_groups g ON g.id=v.option_group_id WHERE g.product_id=? AND v.id IN (" + placeholders(ids.size()) + ")",
					(rs, n) -> rs.getLong(1), args(productId, ids));
			if (groups.size() != ids.size()) throw validation("optionValueIds", "상품에 속하지 않은 옵션 값입니다.");
			if (new LinkedHashSet<>(groups).size() != groups.size()) {
				throw conflict("SKU_OPTION_GROUP_DUPLICATE", "SKU에는 그룹당 하나의 옵션 값만 지정할 수 있습니다.");
			}
			if (sameCombination(productId, skuId, ids)) {
				throw conflict("SKU_OPTION_COMBINATION_CONFLICT", "같은 옵션 조합의 SKU가 이미 있습니다.");
			}
		}
		jdbc.update("DELETE FROM sku_option_values WHERE sku_id=?", skuId);
		for (Long id : ids) jdbc.update("INSERT INTO sku_option_values(sku_id,option_value_id) VALUES (?,?)", skuId, id);
		return new AdminCatalogViews.SkuOptionValues(skuId, ids);
	}

	@Transactional(readOnly = true)
	public AdminCatalogViews.FacetDefinitionList facetDefinitions() {
		List<AdminCatalogViews.FacetDefinition> definitions = jdbc.query(
				"SELECT id,`key`,name FROM facet_definitions ORDER BY id",
				(rs, n) -> new AdminCatalogViews.FacetDefinition(rs.getLong(1), rs.getString(2), rs.getString(3), List.of()));
		return new AdminCatalogViews.FacetDefinitionList(definitions.stream()
				.map(definition -> new AdminCatalogViews.FacetDefinition(definition.facetDefinitionId(), definition.key(),
						definition.name(), facetOptions(definition.facetDefinitionId())))
				.toList());
	}

	@Transactional(readOnly = true)
	public AdminCatalogViews.FacetDefinition facetDefinition(long definitionId) {
		AdminCatalogViews.FacetDefinition definition = jdbc.query(
				"SELECT id,`key`,name FROM facet_definitions WHERE id=?",
				(rs, n) -> new AdminCatalogViews.FacetDefinition(rs.getLong(1), rs.getString(2), rs.getString(3), List.of()), definitionId)
				.stream().findFirst().orElseThrow(() -> missing("FACET_DEFINITION_NOT_FOUND", "Facet 정의를 확인할 수 없습니다."));
		return new AdminCatalogViews.FacetDefinition(definition.facetDefinitionId(), definition.key(), definition.name(), facetOptions(definitionId));
	}

	@Transactional
	public AdminCatalogViews.FacetDefinition createFacetDefinition(AdminCatalogRequests.FacetDefinitionCreate request) {
		try {
			jdbc.update("INSERT INTO facet_definitions(`key`,name) VALUES (?,?)", slug(request.key(), "key"), request.name());
		} catch (DataIntegrityViolationException exception) {
			throw conflict("FACET_KEY_CONFLICT", "이미 사용 중인 facet key입니다.");
		}
		return facetDefinition(jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class));
	}

	@Transactional
	public AdminCatalogViews.FacetDefinition updateFacetDefinition(long definitionId, AdminCatalogRequests.FacetDefinitionPatch request) {
		requirePatch(request.isKeyPresent() || request.isNamePresent());
		AdminCatalogViews.FacetDefinition current = facetDefinition(definitionId);
		String key = request.isKeyPresent() ? slug(request.getKey(), "key") : current.key();
		String name = request.isNamePresent() ? requiredText(request.getName(), "name", 100) : current.name();
		try {
			jdbc.update("UPDATE facet_definitions SET `key`=?,name=? WHERE id=?", key, name, definitionId);
		} catch (DataIntegrityViolationException exception) {
			throw conflict("FACET_KEY_CONFLICT", "이미 사용 중인 facet key입니다.");
		}
		return facetDefinition(definitionId);
	}

	@Transactional
	public void deleteFacetDefinition(long definitionId) {
		try {
			if (jdbc.update("DELETE FROM facet_definitions WHERE id=?", definitionId) == 0) {
				throw missing("FACET_DEFINITION_NOT_FOUND", "Facet 정의를 확인할 수 없습니다.");
			}
		} catch (DataIntegrityViolationException exception) {
			throw conflict("FACET_DEFINITION_IN_USE", "카테고리 또는 상품에 연결된 facet 정의는 삭제할 수 없습니다.");
		}
	}

	@Transactional
	public AdminCatalogViews.FacetOption createFacetOption(long definitionId, AdminCatalogRequests.FacetOptionCreate request) {
		facetDefinition(definitionId);
		try {
			jdbc.update("INSERT INTO facet_options(facet_definition_id,value,display_order) VALUES (?,?,?)",
					definitionId, request.value(), request.displayOrder());
		} catch (DataIntegrityViolationException exception) {
			throw conflict("FACET_OPTION_CONFLICT", "Facet 옵션 값은 중복될 수 없습니다.");
		}
		return facetOption(definitionId, jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class));
	}

	@Transactional
	public AdminCatalogViews.FacetOption updateFacetOption(long definitionId, long optionId, AdminCatalogRequests.FacetOptionPatch request) {
		requirePatch(request.isValuePresent() || request.isDisplayOrderPresent());
		AdminCatalogViews.FacetOption current = facetOption(definitionId, optionId);
		String value = request.isValuePresent() ? requiredText(request.getValue(), "value", 100) : current.value();
		int displayOrder = request.isDisplayOrderPresent()
				? nonNegativeRequired(request.getDisplayOrder(), "displayOrder") : current.displayOrder();
		try {
			jdbc.update("UPDATE facet_options SET value=?,display_order=? WHERE id=? AND facet_definition_id=?",
					value, displayOrder, optionId, definitionId);
		} catch (DataIntegrityViolationException exception) {
			throw conflict("FACET_OPTION_CONFLICT", "Facet 옵션 값은 중복될 수 없습니다.");
		}
		return facetOption(definitionId, optionId);
	}

	@Transactional
	public void deleteFacetOption(long definitionId, long optionId) {
		facetOption(definitionId, optionId);
		try {
			jdbc.update("DELETE FROM facet_options WHERE id=? AND facet_definition_id=?", optionId, definitionId);
		} catch (DataIntegrityViolationException exception) {
			throw conflict("FACET_OPTION_IN_USE", "상품에 연결된 facet 옵션은 삭제할 수 없습니다.");
		}
	}

	@Transactional
	public AdminCatalogViews.CategoryFacet assignCategoryFacet(long categoryId, long definitionId, AdminCatalogRequests.CategoryFacetAssign request) {
		requireCategory(categoryId);
		facetDefinition(definitionId);
		try {
			jdbc.update("INSERT INTO category_facets(category_id,facet_definition_id,display_order) VALUES (?,?,?) ON DUPLICATE KEY UPDATE display_order=VALUES(display_order)",
					categoryId, definitionId, request.displayOrder());
		} catch (DataIntegrityViolationException exception) {
			throw validation("displayOrder", "0 이상이어야 합니다.");
		}
		return new AdminCatalogViews.CategoryFacet(categoryId, definitionId, request.displayOrder());
	}

	@Transactional
	public void removeCategoryFacet(long categoryId, long definitionId) {
		requireCategory(categoryId);
		lockAllProducts();
		lockCategoryFacetScope(categoryId);
		if (jdbc.queryForObject(
				"SELECT COUNT(*) FROM category_facets WHERE category_id=? AND facet_definition_id=?",
				Long.class, categoryId, definitionId) == 0) {
			throw missing("CATEGORY_FACET_NOT_FOUND", "카테고리 facet 배정을 확인할 수 없습니다.");
		}
		boolean inUse = !jdbc.query("""
				SELECT pfv.product_id
				FROM product_facet_values pfv
				JOIN facet_options fo ON fo.id=pfv.facet_option_id
				JOIN products p ON p.id=pfv.product_id
				WHERE p.category_id=? AND fo.facet_definition_id=?
				LIMIT 1
				FOR UPDATE
				""", (rs, n) -> rs.getLong(1), categoryId, definitionId).isEmpty();
		if (inUse) {
			throw conflict("CATEGORY_FACET_IN_USE", "상품이 사용 중인 facet 배정은 제거할 수 없습니다.");
		}
		jdbc.update("DELETE FROM category_facets WHERE category_id=? AND facet_definition_id=?", categoryId, definitionId);
	}

	@Transactional
	public AdminCatalogViews.ProductFacetValues setProductFacetValues(long productId, AdminCatalogRequests.ProductFacetValues request) {
		long categoryId = lockProductAndGetCategory(productId);
		lockCategoryFacetScope(categoryId);
		List<Long> ids = distinct(request.facetOptionIds(), "facetOptionIds");
		if (!ids.isEmpty()) {
			List<Long> allowed = jdbc.query(
					"SELECT fo.id FROM facet_options fo JOIN category_facets cf ON cf.facet_definition_id=fo.facet_definition_id WHERE cf.category_id=? AND fo.id IN (" + placeholders(ids.size()) + ")",
					(rs, n) -> rs.getLong(1), args(categoryId, ids));
			if (allowed.size() != ids.size()) {
				throw conflict("PRODUCT_FACET_NOT_ALLOWED", "상품 카테고리에 허용되지 않은 facet 옵션입니다.");
			}
		}
		jdbc.update("DELETE FROM product_facet_values WHERE product_id=?", productId);
		for (Long id : ids) jdbc.update("INSERT INTO product_facet_values(product_id,facet_option_id) VALUES (?,?)", productId, id);
		cacheInvalidator.invalidateAfterCommit();
		return new AdminCatalogViews.ProductFacetValues(productId, ids);
	}

	private AdminCatalogViews.Brand brand(long id, String name, String slug, String logo, boolean active, int order) {
		return new AdminCatalogViews.Brand(id, name, slug, logo, active, order);
	}

	private AdminCatalogViews.Image image(java.sql.ResultSet rs) throws java.sql.SQLException {
		return new AdminCatalogViews.Image(rs.getLong(1), rs.getLong(2), rs.getString(3), rs.getString(4), rs.getInt(5), rs.getString(6));
	}

	private AdminCatalogViews.Image image(long productId, long imageId) {
		return jdbc.query("SELECT id,product_id,image_url,alt_text,display_order,image_type FROM product_images WHERE product_id=? AND id=?",
				(rs, n) -> image(rs), productId, imageId).stream().findFirst()
				.orElseThrow(() -> missing("PRODUCT_IMAGE_NOT_FOUND", "상품 이미지를 확인할 수 없습니다."));
	}

	private AdminCatalogViews.OptionGroup optionGroup(long productId, long groupId) {
		return jdbc.query("SELECT id,product_id,name,display_order FROM product_option_groups WHERE product_id=? AND id=?",
				(rs, n) -> new AdminCatalogViews.OptionGroup(rs.getLong(1), rs.getLong(2), rs.getString(3), rs.getInt(4), optionValues(rs.getLong(1))),
				productId, groupId).stream().findFirst()
				.orElseThrow(() -> missing("OPTION_GROUP_NOT_FOUND", "옵션 그룹을 확인할 수 없습니다."));
	}

	private List<AdminCatalogViews.OptionValue> optionValues(long groupId) {
		return jdbc.query("SELECT id,option_group_id,value,display_order FROM product_option_values WHERE option_group_id=? ORDER BY display_order,id",
				(rs, n) -> new AdminCatalogViews.OptionValue(rs.getLong(1), rs.getLong(2), rs.getString(3), rs.getInt(4)), groupId);
	}

	private AdminCatalogViews.OptionValue optionValue(long groupId, long valueId) {
		return jdbc.query("SELECT id,option_group_id,value,display_order FROM product_option_values WHERE option_group_id=? AND id=?",
				(rs, n) -> new AdminCatalogViews.OptionValue(rs.getLong(1), rs.getLong(2), rs.getString(3), rs.getInt(4)), groupId, valueId)
				.stream().findFirst().orElseThrow(() -> missing("OPTION_VALUE_NOT_FOUND", "옵션 값을 확인할 수 없습니다."));
	}

	private AdminCatalogViews.OptionValue optionValueForProduct(long productId, long groupId, long valueId) {
		optionGroup(productId, groupId);
		return optionValue(groupId, valueId);
	}

	private List<AdminCatalogViews.FacetOption> facetOptions(long definitionId) {
		return jdbc.query("SELECT id,facet_definition_id,value,display_order FROM facet_options WHERE facet_definition_id=? ORDER BY display_order,id",
				(rs, n) -> new AdminCatalogViews.FacetOption(rs.getLong(1), rs.getLong(2), rs.getString(3), rs.getInt(4)), definitionId);
	}

	private AdminCatalogViews.FacetOption facetOption(long definitionId, long optionId) {
		return jdbc.query("SELECT id,facet_definition_id,value,display_order FROM facet_options WHERE facet_definition_id=? AND id=?",
				(rs, n) -> new AdminCatalogViews.FacetOption(rs.getLong(1), rs.getLong(2), rs.getString(3), rs.getInt(4)), definitionId, optionId)
				.stream().findFirst().orElseThrow(() -> missing("FACET_OPTION_NOT_FOUND", "Facet 옵션을 확인할 수 없습니다."));
	}

	private void lockProduct(long productId) {
		if (jdbc.query("SELECT id FROM products WHERE id=? FOR UPDATE", (rs, n) -> rs.getLong(1), productId).isEmpty()) {
			throw missing("PRODUCT_NOT_FOUND", "상품을 확인할 수 없습니다.");
		}
	}

	private long lockProductAndGetCategory(long productId) {
		return jdbc.query("SELECT category_id FROM products WHERE id=? FOR UPDATE", (rs, n) -> rs.getLong(1), productId)
				.stream().findFirst().orElseThrow(() -> missing("PRODUCT_NOT_FOUND", "상품을 확인할 수 없습니다."));
	}

	private void lockAllProducts() {
		jdbc.query("SELECT id FROM products ORDER BY id FOR UPDATE", (rs, n) -> rs.getLong(1));
	}

	private void lockCategoryFacetScope(long categoryId) {
		jdbc.query("SELECT facet_definition_id FROM category_facets WHERE category_id=? ORDER BY facet_definition_id FOR UPDATE",
				(rs, n) -> rs.getLong(1), categoryId);
	}

	private long requireProduct(long productId) {
		return jdbc.query("SELECT category_id FROM products WHERE id=?", (rs, n) -> rs.getLong(1), productId).stream().findFirst()
				.orElseThrow(() -> missing("PRODUCT_NOT_FOUND", "상품을 확인할 수 없습니다."));
	}

	private void requireSku(long productId, long skuId) {
		if (jdbc.queryForObject("SELECT COUNT(*) FROM skus WHERE id=? AND product_id=?", Long.class, skuId, productId) == 0) {
			throw missing("SKU_NOT_FOUND", "SKU를 확인할 수 없습니다.");
		}
	}

	private void requireCategory(long categoryId) {
		if (jdbc.queryForObject("SELECT COUNT(*) FROM categories WHERE id=?", Long.class, categoryId) == 0) {
			throw missing("CATEGORY_NOT_FOUND", "카테고리를 확인할 수 없습니다.");
		}
	}

	private boolean sameCombination(long productId, long skuId, List<Long> ids) {
		String sql = "SELECT s.id FROM skus s LEFT JOIN sku_option_values sov ON sov.sku_id=s.id WHERE s.product_id=? AND s.id<>? GROUP BY s.id HAVING COUNT(sov.option_value_id)=? AND SUM(sov.option_value_id IN (" + placeholders(ids.size()) + "))=? LIMIT 1";
		List<Object> arguments = new ArrayList<>();
		arguments.add(productId);
		arguments.add(skuId);
		arguments.add(ids.size());
		arguments.addAll(ids);
		arguments.add(ids.size());
		return !jdbc.query(sql, (rs, n) -> rs.getLong(1), arguments.toArray()).isEmpty();
	}

	private Object[] args(Object first, List<Long> ids) {
		List<Object> all = new ArrayList<>();
		all.add(first);
		all.addAll(ids);
		return all.toArray();
	}

	private String placeholders(int size) {
		return String.join(",", java.util.Collections.nCopies(size, "?"));
	}

	private List<Long> distinct(List<Long> values, String field) {
		if (values == null) throw validation(field, "필수 입력입니다.");
		Set<Long> set = new LinkedHashSet<>(values);
		if (set.size() != values.size()) throw validation(field, "중복 값은 허용되지 않습니다.");
		return List.copyOf(set);
	}

	private void requirePatch(boolean hasField) {
		if (!hasField) throw validation("request", "수정할 필드를 하나 이상 입력해 주세요.");
	}

	private String requiredText(String value, String field, int maxLength) {
		if (value == null || value.isBlank()) throw validation(field, "필수 입력입니다.");
		if (value.length() > maxLength) throw validation(field, "길이가 허용 범위를 초과했습니다.");
		return value;
	}

	private String nullableText(String value, String field, int maxLength) {
		if (value != null && value.length() > maxLength) throw validation(field, "길이가 허용 범위를 초과했습니다.");
		return value;
	}

	private boolean requiredBoolean(Boolean value, String field) {
		if (value == null) throw validation(field, "필수 입력입니다.");
		return value;
	}

	private String slug(String value, String field) {
		requiredText(value, field, 100);
		if (!value.matches("[a-z0-9]+(?:-[a-z0-9]+)*")) throw validation(field, "slug 형식이 올바르지 않습니다.");
		return value;
	}

	private String imageType(String value) {
		if (value == null) throw validation("imageType", "필수 입력입니다.");
		if (!"MAIN".equals(value) && !"DETAIL".equals(value)) throw validation("imageType", "MAIN 또는 DETAIL이어야 합니다.");
		return value;
	}

	private int nonNegativeRequired(Integer value, String field) {
		if (value == null) throw validation(field, "필수 입력입니다.");
		if (value < 0) throw validation(field, "0 이상이어야 합니다.");
		return value;
	}

	private AdminCatalogNotFoundException missing(String code, String message) {
		return new AdminCatalogNotFoundException(code, message);
	}

	private AdminCatalogConflictException conflict(String code, String message) {
		return new AdminCatalogConflictException(code, message);
	}

	private AdminCatalogValidationException validation(String field, String message) {
		return new AdminCatalogValidationException(List.of(new FieldErrorResponse(field, message)));
	}
}