package com.pawcycle.backend.catalog.sku.domain;

import com.pawcycle.backend.catalog.product.domain.Product;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;

@Entity
@Table(name = "skus")
public class Sku {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "product_id", nullable = false)
	private Product product;

	@Column(nullable = false, length = 200)
	private String name;

	@Column(nullable = false, precision = 12, scale = 2)
	private BigDecimal price;

	@Column(nullable = false)
	private boolean subscribable;

	@Column(name = "display_order", nullable = false)
	private int displayOrder;

	protected Sku() {
	}

	public Sku(Product product, String name, BigDecimal price, boolean subscribable, int displayOrder) {
		this.product = product;
		this.name = name;
		this.price = price;
		this.subscribable = subscribable;
		this.displayOrder = displayOrder;
	}

	public Long getId() {
		return id;
	}

	public Product getProduct() {
		return product;
	}

	public String getName() {
		return name;
	}

	public BigDecimal getPrice() {
		return price;
	}

	public boolean isSubscribable() {
		return subscribable;
	}
}
