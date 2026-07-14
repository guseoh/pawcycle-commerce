package com.pawcycle.backend.subscription.application;

public class SubscriptionNotFoundException extends RuntimeException {

	public SubscriptionNotFoundException() {
		super("구독을 확인할 수 없습니다.");
	}
}
