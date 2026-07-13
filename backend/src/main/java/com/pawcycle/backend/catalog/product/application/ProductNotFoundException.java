package com.pawcycle.backend.catalog.product.application;

public class ProductNotFoundException extends RuntimeException {

	public ProductNotFoundException() {
		super("상품을 확인할 수 없습니다.");
	}
}
