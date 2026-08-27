package com.pawcycle.backend.catalog.admin.api;

import com.fasterxml.jackson.annotation.JsonSetter;
import com.pawcycle.backend.catalog.product.domain.ProductStatus;
import com.pawcycle.backend.catalog.sku.domain.SkuStatus;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import lombok.Getter;
import lombok.NoArgsConstructor;

public final class AdminCatalogRequests {
	private AdminCatalogRequests() {
	}

	public record CategoryCreate(
			@NotBlank @Size(max = 100) String name,
			@NotBlank @Size(max = 100)
			@Pattern(regexp = "[a-z0-9]+(?:-[a-z0-9]+)*") String slug,
			@NotNull @PositiveOrZero Integer displayOrder,
			@NotNull Boolean active,
			@Positive Long parentId) {
		public CategoryCreate(String name, String slug, Integer displayOrder, Boolean active) { this(name, slug, displayOrder, active, null); }
	}

	public record BrandCreate(@NotBlank @Size(max = 150) String name,
			@NotBlank @Size(max = 100) @Pattern(regexp = "[a-z0-9]+(?:-[a-z0-9]+)*") String slug,
			@Size(max = 2048) String logoUrl, @NotNull Boolean active, @NotNull @PositiveOrZero Integer displayOrder) {}
	public record BrandPatch(String name, String slug, String logoUrl, Boolean active, Integer displayOrder) {}

	public record ProductCreate(
			@NotNull @Positive Long categoryId, @NotNull @Positive Long brandId,
			@NotBlank @Size(max = 200) String name,
			@NotBlank @Size(max = 500) String shortDescription,
			@Size(max = 2000) String description,
			@NotBlank @Size(max = 20) String petType,
			@Size(max = 2048) String thumbnailUrl) {
		public ProductCreate(Long categoryId, String name, String shortDescription, String description, String petType, String thumbnailUrl) { this(categoryId, 1L, name, shortDescription, description, petType, thumbnailUrl); }
	}

	public record SkuCreate(
			@NotBlank @Size(max = 100)
			@Pattern(regexp = "[A-Za-z0-9][A-Za-z0-9._-]*") String skuCode,
			@NotBlank @Size(max = 200) String name,
			@NotNull @DecimalMin("0.00") @Digits(integer = 10, fraction = 2) BigDecimal price,
			@DecimalMin("0.00") @Digits(integer = 10, fraction = 2) BigDecimal compareAtPrice,
			@NotNull Boolean subscribable,
			@NotNull @PositiveOrZero Integer displayOrder,
			@NotNull SkuStatus status) {
		public SkuCreate(String skuCode, String name, BigDecimal price, Boolean subscribable, Integer displayOrder, SkuStatus status) {
			this(skuCode, name, price, null, subscribable, displayOrder, status);
		}
	}

	public record ImageCreate(@NotBlank @Size(max = 2048) String imageUrl, @Size(max = 500) String altText,
			@NotNull @PositiveOrZero Integer displayOrder, @NotBlank @Pattern(regexp = "MAIN|DETAIL") String imageType) {}
	public record ImagePatch(String imageUrl, String altText, Integer displayOrder, String imageType) {}
	public record OptionGroupCreate(@NotBlank @Size(max = 100) String name, @NotNull @PositiveOrZero Integer displayOrder) {}
	public record OptionGroupPatch(String name, Integer displayOrder) {}
	public record OptionValueCreate(@NotBlank @Size(max = 100) String value, @NotNull @PositiveOrZero Integer displayOrder) {}
	public record OptionValuePatch(String value, Integer displayOrder) {}
	public record SkuOptionValues(@NotNull java.util.List<@Positive Long> optionValueIds) {}
	public record FacetDefinitionCreate(@NotBlank @Size(max = 100) @Pattern(regexp = "[a-z0-9]+(?:-[a-z0-9]+)*") String key,
			@NotBlank @Size(max = 100) String name) {}
	public record FacetDefinitionPatch(String key, String name) {}
	public record FacetOptionCreate(@NotBlank @Size(max = 100) String value, @NotNull @PositiveOrZero Integer displayOrder) {}
	public record FacetOptionPatch(String value, Integer displayOrder) {}
	public record CategoryFacetAssign(@NotNull @PositiveOrZero Integer displayOrder) {}
	public record ProductFacetValues(@NotNull java.util.List<@Positive Long> facetOptionIds) {}

	public record DetailSectionCreate(
			@NotBlank @Size(max = 200) String title,
			@NotBlank @Size(max = 10000) String body,
			@NotNull @PositiveOrZero Integer displayOrder,
			@NotNull Boolean visible) {
	}

	@Getter
	@NoArgsConstructor
	public static final class CategoryPatch {
		private String name;
		private boolean namePresent;
		private String slug;
		private boolean slugPresent;
		private Integer displayOrder;
		private boolean displayOrderPresent;
		private Boolean active;
		private boolean activePresent;
		private Long parentId;
		private boolean parentIdPresent;

		@JsonSetter("name") public void readName(String value) { name = value; namePresent = true; }
		@JsonSetter("slug") public void readSlug(String value) { slug = value; slugPresent = true; }
		@JsonSetter("displayOrder") public void readDisplayOrder(Integer value) { displayOrder = value; displayOrderPresent = true; }
		@JsonSetter("active") public void readActive(Boolean value) { active = value; activePresent = true; }
		@JsonSetter("parentId") public void readParentId(Long value) { parentId = value; parentIdPresent = true; }
	}

	@Getter @NoArgsConstructor
	public static final class ProductPatch {
		private Long categoryId; private boolean categoryIdPresent; private Long brandId; private boolean brandIdPresent;
		private String name; private boolean namePresent; private String shortDescription; private boolean shortDescriptionPresent;
		private String description; private boolean descriptionPresent; private String petType; private boolean petTypePresent;
		private String thumbnailUrl; private boolean thumbnailUrlPresent; private ProductStatus status; private boolean statusPresent;
		@JsonSetter("categoryId") public void readCategoryId(Long value) { categoryId=value; categoryIdPresent=true; }
		@JsonSetter("brandId") public void readBrandId(Long value) { brandId=value; brandIdPresent=true; }
		@JsonSetter("name") public void readName(String value) { name=value; namePresent=true; }
		@JsonSetter("shortDescription") public void readShortDescription(String value) { shortDescription=value; shortDescriptionPresent=true; }
		@JsonSetter("description") public void readDescription(String value) { description=value; descriptionPresent=true; }
		@JsonSetter("petType") public void readPetType(String value) { petType=value; petTypePresent=true; }
		@JsonSetter("thumbnailUrl") public void readThumbnailUrl(String value) { thumbnailUrl=value; thumbnailUrlPresent=true; }
		@JsonSetter("status") public void readStatus(ProductStatus value) { status=value; statusPresent=true; }
	}

	@Getter
	@NoArgsConstructor
	/* legacy patch declaration retained below intentionally removed */
	public static final class LegacyProductPatch {
		private Long categoryId;
		private boolean categoryIdPresent;
		private String name;
		private boolean namePresent;
		private String shortDescription;
		private boolean shortDescriptionPresent;
		private String description;
		private boolean descriptionPresent;
		private String petType;
		private boolean petTypePresent;
		private String thumbnailUrl;
		private boolean thumbnailUrlPresent;
		private ProductStatus status;
		private boolean statusPresent;

		@JsonSetter("categoryId") public void readCategoryId(Long value) { categoryId = value; categoryIdPresent = true; }
		@JsonSetter("name") public void readName(String value) { name = value; namePresent = true; }
		@JsonSetter("shortDescription") public void readShortDescription(String value) { shortDescription = value; shortDescriptionPresent = true; }
		@JsonSetter("description") public void readDescription(String value) { description = value; descriptionPresent = true; }
		@JsonSetter("petType") public void readPetType(String value) { petType = value; petTypePresent = true; }
		@JsonSetter("thumbnailUrl") public void readThumbnailUrl(String value) { thumbnailUrl = value; thumbnailUrlPresent = true; }
		@JsonSetter("status") public void readStatus(ProductStatus value) { status = value; statusPresent = true; }
	}

	@Getter
	@NoArgsConstructor
	public static final class SkuPatch {
		private String name;
		private boolean namePresent;
		private BigDecimal price;
		private boolean pricePresent;
		private BigDecimal compareAtPrice;
		private boolean compareAtPricePresent;
		private Boolean subscribable;
		private boolean subscribablePresent;
		private Integer displayOrder;
		private boolean displayOrderPresent;
		private SkuStatus status;
		private boolean statusPresent;

		@JsonSetter("name") public void readName(String value) { name = value; namePresent = true; }
		@JsonSetter("price") public void readPrice(BigDecimal value) { price = value; pricePresent = true; }
		@JsonSetter("compareAtPrice") public void readCompareAtPrice(BigDecimal value) { compareAtPrice = value; compareAtPricePresent = true; }
		@JsonSetter("subscribable") public void readSubscribable(Boolean value) { subscribable = value; subscribablePresent = true; }
		@JsonSetter("displayOrder") public void readDisplayOrder(Integer value) { displayOrder = value; displayOrderPresent = true; }
		@JsonSetter("status") public void readStatus(SkuStatus value) { status = value; statusPresent = true; }
	}

	@Getter
	@NoArgsConstructor
	public static final class DetailSectionPatch {
		private String title;
		private boolean titlePresent;
		private String body;
		private boolean bodyPresent;
		private Integer displayOrder;
		private boolean displayOrderPresent;
		private Boolean visible;
		private boolean visiblePresent;

		@JsonSetter("title") public void readTitle(String value) { title = value; titlePresent = true; }
		@JsonSetter("body") public void readBody(String value) { body = value; bodyPresent = true; }
		@JsonSetter("displayOrder") public void readDisplayOrder(Integer value) { displayOrder = value; displayOrderPresent = true; }
		@JsonSetter("visible") public void readVisible(Boolean value) { visible = value; visiblePresent = true; }
	}
}
