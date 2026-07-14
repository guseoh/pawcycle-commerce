package com.pawcycle.backend.subscription.application;

public class SkuNotFoundException extends RuntimeException {

	public SkuNotFoundException() {
		super("SKU를 확인할 수 없습니다.");
	}
}
