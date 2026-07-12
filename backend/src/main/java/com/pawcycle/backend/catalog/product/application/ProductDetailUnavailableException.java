package com.pawcycle.backend.catalog.product.application;

public class ProductDetailUnavailableException extends RuntimeException {

	public ProductDetailUnavailableException(Throwable cause) {
		super("Product detail query failed", cause);
	}
}
