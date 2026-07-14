package com.pawcycle.backend.subscription.application;

public class SkuNotSubscribableException extends RuntimeException {

	public SkuNotSubscribableException() {
		super("구독할 수 없는 SKU입니다.");
	}
}
