package com.pawcycle.backend.catalog.category.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "categories")
@Getter
@NoArgsConstructor(access = lombok.AccessLevel.PROTECTED)
public class Category {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false, length = 100)
	private String name;

	@Column(nullable = false, unique = true, length = 100)
	private String slug;

	@Column(name = "display_order", nullable = false)
	private int displayOrder;

	@Column(nullable = false)
	private boolean active;

	public Category(String name, String slug, int displayOrder, boolean active) {
		this.name = name;
		this.slug = slug;
		this.displayOrder = displayOrder;
		this.active = active;
	}

	public void update(String name, String slug, Integer displayOrder, Boolean active) {
		if (name != null) this.name = name;
		if (slug != null) this.slug = slug;
		if (displayOrder != null) this.displayOrder = displayOrder;
		if (active != null) this.active = active;
	}
}
