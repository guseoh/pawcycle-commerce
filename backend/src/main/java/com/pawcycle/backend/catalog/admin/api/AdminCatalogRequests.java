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
			@NotNull Boolean active) {
	}

	public record ProductCreate(
		@NotNull @Positive Long categoryId,
			@NotBlank @Size(max = 200) String name,
			@NotBlank @Size(max = 500) String shortDescription,
			@Size(max = 2000) String description,
			@NotBlank @Size(max = 20) String petType,
			@Size(max = 2048) String thumbnailUrl) {
	}

	public record SkuCreate(
			@NotBlank @Size(max = 100)
			@Pattern(regexp = "[A-Za-z0-9][A-Za-z0-9._-]*") String skuCode,
			@NotBlank @Size(max = 200) String name,
			@NotNull @DecimalMin("0.00") @Digits(integer = 10, fraction = 2) BigDecimal price,
			@NotNull Boolean subscribable,
			@NotNull @PositiveOrZero Integer displayOrder,
			@NotNull SkuStatus status) {
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

		@JsonSetter("name") public void readName(String value) { name = value; namePresent = true; }
		@JsonSetter("slug") public void readSlug(String value) { slug = value; slugPresent = true; }
		@JsonSetter("displayOrder") public void readDisplayOrder(Integer value) { displayOrder = value; displayOrderPresent = true; }
		@JsonSetter("active") public void readActive(Boolean value) { active = value; activePresent = true; }
	}

	@Getter
	@NoArgsConstructor
	public static final class ProductPatch {
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
		private Boolean subscribable;
		private boolean subscribablePresent;
		private Integer displayOrder;
		private boolean displayOrderPresent;
		private SkuStatus status;
		private boolean statusPresent;

		@JsonSetter("name") public void readName(String value) { name = value; namePresent = true; }
		@JsonSetter("price") public void readPrice(BigDecimal value) { price = value; pricePresent = true; }
		@JsonSetter("subscribable") public void readSubscribable(Boolean value) { subscribable = value; subscribablePresent = true; }
		@JsonSetter("displayOrder") public void readDisplayOrder(Integer value) { displayOrder = value; displayOrderPresent = true; }
		@JsonSetter("status") public void readStatus(SkuStatus value) { status = value; statusPresent = true; }
	}
}
