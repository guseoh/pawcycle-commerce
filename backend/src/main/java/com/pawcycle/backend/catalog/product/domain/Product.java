package com.pawcycle.backend.catalog.product.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "products")
public class Product {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

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

	@Column(name = "display_status", nullable = false, length = 20)
	private String displayStatus;

	protected Product() {
	}

	public Product(
			String name,
			String shortDescription,
			String description,
			String petType,
			String thumbnailUrl,
			String displayStatus) {
		this.name = name;
		this.shortDescription = shortDescription;
		this.description = description;
		this.petType = petType;
		this.thumbnailUrl = thumbnailUrl;
		this.displayStatus = displayStatus;
	}

	public Long getId() {
		return id;
	}

	public String getName() {
		return name;
	}

	public String getShortDescription() {
		return shortDescription;
	}

	public String getDescription() {
		return description;
	}

	public String getPetType() {
		return petType;
	}

	public String getThumbnailUrl() {
		return thumbnailUrl;
	}

	public String getDisplayStatus() {
		return displayStatus;
	}
}
