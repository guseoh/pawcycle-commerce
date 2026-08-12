package com.pawcycle.backend.catalog.product.domain;

import com.pawcycle.backend.catalog.category.domain.Category;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "products")
@Getter
@NoArgsConstructor(access = lombok.AccessLevel.PROTECTED)
public class Product {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "category_id")
	private Category category;

	@Column(nullable = false, length = 200)
	private String name;

	@Column(name = "short_description", nullable = false, length = 500)
	private String shortDescription;

	@Column(length = 2000)
	private String description;

	@Column(name = "pet_type", nullable = false, length = 20)
	private String petType;

	@Column(name = "thumbnail_url", length = 2048)
	private String thumbnailUrl;

	@Enumerated(EnumType.STRING)
	@Column(name = "display_status", nullable = false, length = 20)
	private ProductStatus status;

	public Product(
			String name,
			String shortDescription,
			String description,
			String petType,
			String thumbnailUrl,
			String displayStatus) {
		this(null, name, shortDescription, description, petType, thumbnailUrl, ProductStatus.valueOf(displayStatus));
	}

	public Product(
			Category category,
			String name,
			String shortDescription,
			String description,
			String petType,
			String thumbnailUrl) {
		this(category, name, shortDescription, description, petType, thumbnailUrl, ProductStatus.DRAFT);
	}

	private Product(
			Category category,
			String name,
			String shortDescription,
			String description,
			String petType,
			String thumbnailUrl,
			ProductStatus status) {
		this.category = category;
		this.name = name;
		this.shortDescription = shortDescription;
		this.description = description;
		this.petType = petType;
		this.thumbnailUrl = thumbnailUrl;
		this.status = status;
	}

	public String getDisplayStatus() {
		return status.name();
	}

	public void update(
			Category category,
			boolean categoryPresent,
			String name,
			String shortDescription,
			String description,
			boolean descriptionPresent,
			String petType,
			String thumbnailUrl,
			boolean thumbnailUrlPresent) {
		if (categoryPresent) this.category = category;
		if (name != null) this.name = name;
		if (shortDescription != null) this.shortDescription = shortDescription;
		if (descriptionPresent) this.description = description;
		if (petType != null) this.petType = petType;
		if (thumbnailUrlPresent) this.thumbnailUrl = thumbnailUrl;
	}

	public boolean canTransitionTo(ProductStatus target) {
		return (status == ProductStatus.DRAFT && target == ProductStatus.PUBLIC)
				|| (status == ProductStatus.PUBLIC && target == ProductStatus.INACTIVE)
				|| (status == ProductStatus.INACTIVE && target == ProductStatus.PUBLIC);
	}

	public void transitionTo(ProductStatus target) {
		if (!canTransitionTo(target)) {
			throw new IllegalStateException("Unsupported product status transition");
		}
		status = target;
	}
}
