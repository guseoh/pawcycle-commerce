package com.pawcycle.backend.catalog.product.application;

public class ProductListUnavailableException extends RuntimeException {

	public ProductListUnavailableException(Throwable cause) {
		super("Product list query failed", cause);
	}
}
